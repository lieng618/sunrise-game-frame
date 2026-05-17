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
            Utils.sleep(interval);
        }
    }

    public void start() {
        stopped = false;
        this.thread.start();
        LogCore.ServerStartUp.info("DispatchThread Start, name = { {} }, { {} }", thread.getName(), thread);
    }

    public void shutdown() {
        if (stopped)
            return;
        stopped = true;
    }
}
