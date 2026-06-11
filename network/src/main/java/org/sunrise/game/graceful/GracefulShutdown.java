package org.sunrise.game.graceful;

import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *   <li>按 {@link OnShutdown#order()} 升序执行各阶段：排空 RPC → 保存数据 → 关闭网络 → 关闭 DB</li>
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
     * 扫描所有已知模块包下的 {@link OnShutdown} 注解方法，按 order 排序注册，
     * 并安装 JVM shutdown hook。
     *
     * <p>等价于 {@code scanAndInstall(DEFAULT_SCAN_PACKAGES)}。</p>
     */
    public static void scanAndInstall() {
        scanAndInstall(DEFAULT_SCAN_PACKAGES);
    }

    /**
     * 扫描指定包下所有 {@link OnShutdown} 注解的静态方法，按 order 排序后注册，
     * 并安装 JVM shutdown hook。
     *
     * <p>被注解的方法必须满足：{@code public static}，无参数。返回值不限（忽略）。</p>
     *
     * @param packageNames 要扫描的包名
     */
    public static void scanAndInstall(String... packageNames) {
        List<ScanEntry> scanned = new ArrayList<>();

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

                        method.setAccessible(true);

                        String name = ann.name().isEmpty()
                                ? clazz.getSimpleName() + "." + method.getName()
                                : ann.name();

                        Runnable task = () -> {
                            try {
                                method.invoke(null);
                            } catch (Exception e) {
                                LogCore.ServerStartUp.error("GracefulShutdown: error invoking {}.{}: {}",
                                        clazz.getSimpleName(), method.getName(), e.getMessage(), e);
                            }
                        };

                        scanned.add(new ScanEntry(ann.order(), name, task, ann.timeoutMs()));
                        LogCore.ServerStartUp.debug("GracefulShutdown: scanned @OnShutdown order={} {} -> {}.{}",
                                ann.order(), name, clazz.getSimpleName(), method.getName());
                    }
                }
            } catch (Exception e) {
                LogCore.ServerStartUp.error("GracefulShutdown: error scanning package {}: {}", pkg, e.getMessage(), e);
            }
        }

        scanned.sort(Comparator.comparingInt(e -> e.order));
        for (ScanEntry entry : scanned) {
            phases.add(new Phase(entry.name, entry.task, entry.timeoutMs));
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

        for (int i = 0; i < phases.size(); i++) {
            Phase phase = phases.get(i);
            LogCore.ServerStartUp.info("GracefulShutdown [{} of {}] {} — starting", i + 1, phases.size(), phase.name);
            long startMs = System.currentTimeMillis();

            try {
                Thread worker = new Thread(phase.task, "GracefulShutdown-" + phase.name);
                worker.start();
                worker.join(phase.timeoutMs);

                if (worker.isAlive()) {
                    LogCore.ServerStartUp.warn("GracefulShutdown [{} of {}] {} — TIMEOUT after {}ms, continuing",
                            i + 1, phases.size(), phase.name, phase.timeoutMs);
                } else {
                    long elapsed = System.currentTimeMillis() - startMs;
                    LogCore.ServerStartUp.info("GracefulShutdown [{} of {}] {} — done in {}ms",
                            i + 1, phases.size(), phase.name, elapsed);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogCore.ServerStartUp.warn("GracefulShutdown [{} of {}] {} — interrupted", i + 1, phases.size(), phase.name);
            } catch (Exception e) {
                LogCore.ServerStartUp.error("GracefulShutdown [{} of {}] {} — error: {}",
                        i + 1, phases.size(), phase.name, e.getMessage(), e);
            }
        }

        LogCore.ServerStartUp.info("==================== GracefulShutdown END ====================");
    }

    /**
     * 是否已触发停机。
     */
    public static boolean isShuttingDown() {
        return triggered.get();
    }

    // ---- internal ----

    private static void install() {
        if (!installed.compareAndSet(false, true)) {
            LogCore.ServerStartUp.warn("GracefulShutdown: already installed");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(GracefulShutdown::trigger, "GracefulShutdown"));
        LogCore.ServerStartUp.info("GracefulShutdown installed with {} phases", phases.size());
    }

    private record ScanEntry(int order, String name, Runnable task, long timeoutMs) {}

    private record Phase(String name, Runnable task, long timeoutMs) {}
}
