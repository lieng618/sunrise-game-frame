package org.sunrise.game.game.async;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AsyncEventManager {
    // 异步回调队列
    public static ConcurrentLinkedQueue<Runnable> asyncQueue = new ConcurrentLinkedQueue<>();

    public static void addAsyncEvent(Runnable task) {
        asyncQueue.add(task);
    }
}
