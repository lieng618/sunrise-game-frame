package org.sunrise.game.core.message;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 消息管理器基类
 * 需实现接收与发送逻辑
 *
 * <p>停机生命周期只允许单向流转：</p>
 * <pre>
 * RUNNING --freeze--> DRAINING --submit--> FINALIZING --stop--> STOPPED
 * </pre>
 *
 * <p>recvQueueLock 同时保护“入口状态判断 + 入队”，因此 freeze 返回后不会再有
 * 新消息越过停止边界进入接收队列。</p>
 */
@Getter
@Setter
public abstract class BaseMessageManager {
    private final String nodeId;
    private AtomicBoolean running = new AtomicBoolean(false);
    private DispatchThread dispatchThread;
    /** 停机时唯一的最终业务任务；只允许注册一次，且仅由 DispatchThread 执行。 */
    private final AtomicReference<DispatchTask> dispatchTask = new AtomicReference<>();
    /** 保护接收入口状态和 recvMsgQueue 替换，确保停机入队具有明确的原子边界。 */
    private final Object recvQueueLock = new Object();
    private volatile Lifecycle lifecycle = Lifecycle.RUNNING;
    /**
     * 入站队列
     **/
    private ConcurrentLinkedQueue<Object> recvMsgQueue = new ConcurrentLinkedQueue<>();
    /**
     * 出站队列
     **/
    private ConcurrentLinkedQueue<Object> sendMsgQueue = new ConcurrentLinkedQueue<>();

    protected BaseMessageManager(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 一次心跳处理接收到的所有数据
     *
     * @return 本次实际 poll 并处理的条数
     */
    public int pulseHandler() {
        int processed = 0;
        while (!recvMsgQueue.isEmpty()) {
            Object data = recvMsgQueue.poll();
            if (data == null) {
                continue;
            }
            processed++;
            pulseHandlerOne(data);
        }
        return processed;
    }

    /**
     * 一次心跳处理要发送到对端的所有数据
     *
     * @return 本次实际发送条数
     */
    public int pulseSender() {
        int sent = 0;
        while (!sendMsgQueue.isEmpty()) {
            Object data = sendMsgQueue.poll();
            if (data == null) {
                continue;
            }
            sent++;
            pulseSenderOne(data);
        }
        return sent;
    }

    /**
     * 单个数据的处理
     */
    protected abstract void pulseHandlerOne(Object data);

    /**
     * 单个数据的处理
     */
    protected abstract void pulseSenderOne(Object data);

    public boolean recvMsg(Object data) {
        if (data == null) {
            return false;
        }
        // 入队与冻结在同一把锁内完成，避免“freeze 已返回但消息随后入队”。
        synchronized (recvQueueLock) {
            if (lifecycle != Lifecycle.RUNNING) {
                return false;
            }
            recvMsgQueue.add(data);
            return true;
        }
    }

    public void sendMsg(Object data) {
        if (data != null) {
            sendMsgQueue.add(data);
        }
    }

    /**
     * 基类心跳接口
     */
    public void pulse() {
        try {
            pulseHandler();
            pulseSender();
            runPendingDispatchTask();
        } catch (Exception e) {
            LogCore.BaseServer.error("DispatchThread pulse, error : ", e);
        }
    }

    public void run() {
        if (!running.get()) {
            running.set(true);
            String handlerName = this.getClass().getSimpleName();
            dispatchThread = new DispatchThread(this::pulse, handlerName);
            dispatchThread.setInterval(5);
            dispatchThread.start();
        } else {
            LogCore.ServerStartUp.warn("DispatchThread Start Failed, name = { {} }, reason = { {} })", this.getClass().getSimpleName(), "repeat run");
        }
    }

    public void setRecvMsgQueue(ConcurrentLinkedQueue<Object> recvMsgQueue) {
        synchronized (recvQueueLock) {
            this.recvMsgQueue = recvMsgQueue;
        }
    }

    /**
     * 原子地关闭入站入口，并暂停业务 pulse。
     *
     * <p>冻结后，已经进入 recv queue 的消息仍由 DispatchThread 处理；心跳、
     * ServiceManager 定时逻辑等不再继续修改业务状态，便于随后执行一致性快照。</p>
     */
    public void freezeRecvQueue() {
        synchronized (recvQueueLock) {
            // 已经进入 DRAINING/FINALIZING 后重复调用不能把生命周期改回去。
            if (lifecycle == Lifecycle.RUNNING) {
                lifecycle = Lifecycle.DRAINING;
            }
        }
    }

    public boolean isAcceptingRecvMessages() {
        return lifecycle == Lifecycle.RUNNING;
    }

    public boolean isBusinessPaused() {
        return lifecycle != Lifecycle.RUNNING;
    }

    /**
     * 在 DispatchThread 处理完冻结前积压的消息后执行任务。
     *
     * <p>任务不会在调用线程执行，也不会再由另一个线程并发消费消息队列。</p>
     *
     * <p>只允许一个最终任务。这样最终快照天然是一个 barrier，不需要多个任务
     * 互相判断“哪个快照先结束”。</p>
     */
    public CompletableFuture<Void> submitAfterDrainOnDispatchThread(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (task == null) {
            future.completeExceptionally(new IllegalArgumentException("task must not be null"));
            return future;
        }
        DispatchThread currentDispatchThread = dispatchThread;
        if (currentDispatchThread == null || currentDispatchThread.isStopped()) {
            future.completeExceptionally(new IllegalStateException(
                    "DispatchThread is not running, nodeId=" + nodeId));
            return future;
        }
        synchronized (recvQueueLock) {
            if (lifecycle != Lifecycle.DRAINING) {
                future.completeExceptionally(new IllegalStateException(
                        "recv queue must be frozen before scheduling a shutdown task"));
                return future;
            }
            if (!dispatchTask.compareAndSet(null, new DispatchTask(task, future))) {
                future.completeExceptionally(new IllegalStateException(
                        "only one shutdown task may follow the final drain, nodeId=" + nodeId));
                return future;
            }
            lifecycle = Lifecycle.FINALIZING;
        }
        return future;
    }

    /**
     * 仅由 DispatchThread 调用。入站队列为空时才执行，确保任务不会越过已有消息。
     */
    protected void runPendingDispatchTask() {
        // 接收队列非空时继续处理原有消息，最终快照绝不能越过它们。
        if (!recvMsgQueue.isEmpty()) {
            return;
        }
        DispatchTask pending = dispatchTask.getAndSet(null);
        if (pending == null) {
            return;
        }
        try {
            pending.task().run();
            pending.future().complete(null);
        } catch (Throwable e) {
            pending.future().completeExceptionally(e);
            LogCore.BaseServer.error("Dispatch shutdown task failed, nodeId={}", nodeId, e);
        }
    }

    public boolean stopDispatch(long timeoutMs) {
        DispatchThread currentDispatchThread;
        synchronized (recvQueueLock) {
            // 先关闭入口，再通知线程退出。即使 stop 期间还有网络回调，
            // recvMsg 也不会再把消息加入队列。
            lifecycle = Lifecycle.STOPPED;
            currentDispatchThread = dispatchThread;
        }
        if (currentDispatchThread == null) {
            return true;
        }
        currentDispatchThread.shutdown();
        return currentDispatchThread.awaitTermination(timeoutMs);
    }

    private record DispatchTask(Runnable task, CompletableFuture<Void> future) {
    }

    private enum Lifecycle {
        /** 正常接收消息并执行周期 pulse。 */
        RUNNING,
        /** 停止接收新消息，允许处理冻结前积压的消息。 */
        DRAINING,
        /** 已注册唯一最终任务，等待 DispatchThread 执行。 */
        FINALIZING,
        /** 不再接收消息，DispatchThread 正在或已经停止。 */
        STOPPED
    }

}
