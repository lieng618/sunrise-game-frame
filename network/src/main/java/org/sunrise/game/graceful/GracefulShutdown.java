package org.sunrise.game.graceful;

import org.sunrise.game.core.message.BaseMessageManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 优雅停机编排器 — 基于 {@link OnShutdown} 注解自动发现并有序执行停机回调。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在启动流程的最后调用
 * GracefulShutdown.scanAndInstall();
 * }</pre>
 *
 * <h3>停机序列</h3>
 * <ol>
 *   <li>设置全局停机标志（拒绝新 RPC、健康检查返回不健康）</li>
 *   <li>按 {@link ShutdownPhase} 顺序执行各阶段：排空 RPC → 冻结业务入队 →
 *       在原 DispatchThread 保存数据 → 关闭调度和网络 → 排空 DB 后关闭连接池</li>
 * </ol>
 *
 * <h3>手动触发</h3>
 * <pre>{@code
 * GracefulShutdown.trigger();  // GM API 等场景
 * }</pre>
 */
public class GracefulShutdown {

    private static final List<Phase> phases = new ArrayList<>();
    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static final AtomicBoolean triggered = new AtomicBoolean(false);
    private static final AtomicBoolean completed = new AtomicBoolean(false);
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final long INTERRUPT_GRACE_MS = 1_000L;
    private static volatile BaseMessageManager businessMessageManager;
    /**
     * 普通停机阶段在独立线程执行，停机协调线程只负责按 timeout 等待。
     * 使用守护线程，避免 JVM 退出时被未完成阶段永久拖住。
     */
    private static final ExecutorService shutdownExecutor =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "GracefulShutdownWorker");
                thread.setDaemon(true);
                return thread;
            });

    private GracefulShutdown() {
    }

    /**
     * 跨模块子包列表，确保 Maven 多模块项目下两个模块的类都能被扫描到。
     * 因为 {@link Utils#findClasses} 底层用 {@code ClassLoader.getResource()}（单数），
     * 同一包前缀在两个模块中只会命中第一个 classpath 条目。
     */
    private static final String[] DEFAULT_SCAN_PACKAGES = {
            "org.sunrise.game.core",
            "org.sunrise.game.db",
            "org.sunrise.game.rpc",
            "org.sunrise.game.game",
            "org.sunrise.game.external",
            "org.sunrise.game.gmback",
            "org.sunrise.game.global",
            "org.sunrise.game.http",
    };

    // ---- public API ----

    /**
     * 注册承载业务串行队列的 RPC Server MessageManager。
     * 进程中只允许存在一个 RPC Node，因此也只应注册一个。
     */
    public static void registerBusinessMessageManager(BaseMessageManager messageManager) {
        if (businessMessageManager != null && businessMessageManager != messageManager) {
            throw new IllegalStateException("business message manager already registered");
        }
        businessMessageManager = messageManager;
    }

    /**
     * 扫描所有已知模块包下的 {@link OnShutdown} 注解方法，按固定阶段注册，
     * 并安装 JVM shutdown hook。
     *
     * <p>等价于 {@code scanAndInstall(DEFAULT_SCAN_PACKAGES)}。</p>
     */
    public static void scanAndInstall() {
        scanAndInstall(DEFAULT_SCAN_PACKAGES);
    }

    /**
     * 扫描指定包下所有 {@link OnShutdown} 注解的静态方法，按固定阶段注册，
     * 并安装 JVM shutdown hook。
     *
     * <p>被注解的方法必须满足：{@code public static}，无参数。返回值不限（忽略）。</p>
     *
     * @param packageNames 要扫描的包名
     */
    public static void scanAndInstall(String... packageNames) {
        List<ScanEntry> scanned = new ArrayList<>();
        Set<ShutdownPhase> registeredPhases = EnumSet.noneOf(ShutdownPhase.class);

        for (String pkg : packageNames) {
            try {
                for (Class<?> clazz : Utils.findClasses(pkg)) {
                    for (Method method : clazz.getDeclaredMethods()) {
                        OnShutdown ann = method.getAnnotation(OnShutdown.class);
                        if (ann == null) continue;

                        if (!Modifier.isStatic(method.getModifiers())
                                || !Modifier.isPublic(method.getModifiers())
                                || method.getParameterCount() != 0) {
                            LogCore.ServerStartUp.warn(
                                    "GracefulShutdown: @OnShutdown method must be 'public static' with no params — skipped: {}.{}",
                                    clazz.getSimpleName(), method.getName());
                            continue;
                        }
                        if (!registeredPhases.add(ann.phase())) {
                            LogCore.ServerStartUp.error(
                                    "GracefulShutdown: duplicate phase {} ignored at {}.{}",
                                    ann.phase(), clazz.getName(), method.getName());
                            continue;
                        }

                        method.setAccessible(true);

                        String name = ann.name().isEmpty()
                                ? clazz.getSimpleName() + "." + method.getName()
                                : ann.name();

                        Runnable task = () -> {
                            try {
                                method.invoke(null);
                            } catch (InvocationTargetException e) {
                                throw propagate(e.getCause());
                            } catch (ReflectiveOperationException e) {
                                throw new IllegalStateException(
                                        "Failed to invoke shutdown method " + clazz.getName() + "." + method.getName(), e);
                            }
                        };

                        scanned.add(new ScanEntry(
                                ann.phase(), name, task, ann.timeoutMs()));
                        LogCore.ServerStartUp.debug(
                                "GracefulShutdown: scanned @OnShutdown phase={} {} -> {}.{}",
                                ann.phase(), name, clazz.getSimpleName(), method.getName());
                    }
                }
            } catch (Exception e) {
                LogCore.ServerStartUp.error("GracefulShutdown: error scanning package {}: {}", pkg, e.getMessage(), e);
            }
        }

        scanned.sort(Comparator
                .comparingInt((ScanEntry e) -> e.phase().ordinal())
                .thenComparing(ScanEntry::name));
        for (ScanEntry entry : scanned) {
            phases.add(new Phase(entry.phase, entry.name, entry.task, entry.timeoutMs));
        }

        install();
    }

    /**
     * 触发停机（由 shutdown hook 自动调用，或手动调用如 GM API）。
     * 幂等：多次调用只有第一次生效。
     */
    public static void trigger() {
        if (!triggered.compareAndSet(false, true)) {
            LogCore.ServerStartUp.warn("GracefulShutdown: already in progress, skipping duplicate trigger");
            return;
        }

        LogCore.ServerStartUp.info("==================== GracefulShutdown START ====================");

        // Phase 0: 先设全局标志（内置，不由注解驱动）
        RpcFunction.shuttingDown = true;
        // 扫描顺序和运行顺序都归一化，避免 classpath 遍历顺序影响停机结果。
        phases.sort(Comparator
                .comparingInt((Phase phase) -> phase.phase().ordinal())
                .thenComparing(Phase::name));

        for (int i = 0; i < phases.size(); i++) {
            Phase phase = phases.get(i);
            LogCore.ServerStartUp.info("GracefulShutdown [{} of {}] {} — starting", i + 1, phases.size(), phase.name);
            long startMs = System.currentTimeMillis();

            try {
                if (phase.phase().usesBusinessThread()) {
                    executeOnBusinessThread(phase);
                } else {
                    executeOnShutdownThread(phase);
                }
                long elapsed = System.currentTimeMillis() - startMs;
                LogCore.ServerStartUp.info("GracefulShutdown [{} of {}] {} — done in {}ms",
                        i + 1, phases.size(), phase.name, elapsed);
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
                // 数据保存失败时继续关闭网络/DB 会扩大不可恢复风险；
                // 因此保留后续资源，便于排查和人工处理。
                LogCore.ServerStartUp.error(
                        "GracefulShutdown [{} of {}] {} — FAILED, remaining phases are skipped: {}",
                        i + 1, phases.size(), phase.name, e.getMessage(), e);
                int skipped = phases.size() - i - 1;
                if (skipped > 0) {
                    LogCore.ServerStartUp.error(
                            "GracefulShutdown incomplete: {} later phase(s) skipped; resources are intentionally left open",
                            skipped);
                }
                break;
            }
        }

        if (failure.get() == null) {
            completed.set(true);
            LogCore.ServerStartUp.info("==================== GracefulShutdown END ====================");
        } else {
            LogCore.ServerStartUp.error("==================== GracefulShutdown INCOMPLETE ====================");
        }
    }

    /**
     * 是否已触发停机。
     */
    public static boolean isShuttingDown() {
        return triggered.get();
    }

    public static boolean isCompleted() {
        return completed.get();
    }

    public static Throwable getFailure() {
        return failure.get();
    }

    // ---- internal ----

    /**
     * 只提交任务，不在停机线程直接操作业务对象。future 完成代表该任务之前的
     * 消息已经由原 DispatchThread 排空，并且最终快照已经在同一线程执行完毕。
     */
    private static void executeOnBusinessThread(Phase phase) throws Throwable {
        BaseMessageManager manager = businessMessageManager;
        if (manager == null) {
            throw new IllegalStateException(
                    "No business DispatchThread registered for shutdown phase " + phase.name);
        }

        CompletableFuture<Void> future = manager.submitAfterDrainOnDispatchThread(phase.task);
        try {
            future.get(phase.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            manager.stopDispatch(INTERRUPT_GRACE_MS);
            future.cancel(true);
            TimeoutException timeout = new TimeoutException(
                    "shutdown phase " + phase.name + " timed out after " + phase.timeoutMs
                            + "ms; DispatchThread stop requested");
            timeout.initCause(e);
            throw timeout;
        } catch (ExecutionException e) {
            throw e.getCause() == null ? e : e.getCause();
        } catch (InterruptedException e) {
            manager.stopDispatch(INTERRUPT_GRACE_MS);
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new InterruptedException("shutdown phase " + phase.name + " was interrupted");
        }
    }

    /**
     * 用于等待 RPC、Controller 或网络资源等可能阻塞的普通阶段。
     * 超时后取消阶段线程并交由 trigger 的 fail-fast 逻辑终止后续阶段。
     */
    private static void executeOnShutdownThread(Phase phase) throws Throwable {
        Future<?> future = shutdownExecutor.submit(phase.task);
        try {
            future.get(phase.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException(
                    "shutdown phase " + phase.name + " timed out after " + phase.timeoutMs + "ms");
        } catch (ExecutionException e) {
            throw e.getCause() == null ? e : e.getCause();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new InterruptedException("shutdown phase " + phase.name + " was interrupted");
        }
    }

    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private static void install() {
        if (!installed.compareAndSet(false, true)) {
            LogCore.ServerStartUp.warn("GracefulShutdown: already installed");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(GracefulShutdown::trigger, "GracefulShutdown"));
        LogCore.ServerStartUp.info("GracefulShutdown installed with {} phases", phases.size());
    }

    private record ScanEntry(
            ShutdownPhase phase, String name, Runnable task, long timeoutMs) {
    }

    record Phase(ShutdownPhase phase, String name, Runnable task, long timeoutMs) {
    }
}
