package org.sunrise.game.thread;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

public class DispatchThread {
    private final Thread thread;
    private final Runnable task;
    @Setter
    private int interval = 1;
    @Setter
    @Getter
    private volatile boolean stopped = false;

    public DispatchThread(Runnable task) {
        this.task = task;
        this.thread = new Thread(this::run);
    }

    public DispatchThread(Runnable task, String name) {
        this.task = task;
        this.thread = new Thread(this::run, name);
    }

    private void run() {
        while (!stopped) {
            try {
                task.run();
            } catch (Exception e) {
                LogCore.ServerStartUp.error("DispatchThread pulse, error : ", e);
            }
            // sleep 被中断或在 stopped 已为 true 时快速退出
            if (!Utils.sleep(interval) && stopped) {
                break;
            }
        }
        LogCore.ServerStartUp.info("DispatchThread exit, name = { {} }", thread.getName());
    }

    public void start() {
        stopped = false;
        this.thread.start();
        LogCore.ServerStartUp.info("DispatchThread Start, name = { {} }, { {} }", thread.getName(), thread);
    }

    /**
     * 优雅关闭：设停止标志并中断线程，使其尽快退出 sleep 状态。
     * 如需等待线程完全退出，请调用 {@link #awaitTermination(long)}。
     */
    public void shutdown() {
        if (stopped)
            return;
        stopped = true;
        thread.interrupt();
    }

    /**
     * 等待线程退出，最长等待 timeoutMs 毫秒。
     *
     * @param timeoutMs 最大等待毫秒数
     * @return true 如果线程已在超时前退出；false 如果超时
     */
    public boolean awaitTermination(long timeoutMs) {
        try {
            thread.join(timeoutMs);
            return !thread.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !thread.isAlive();
        }
    }
}
