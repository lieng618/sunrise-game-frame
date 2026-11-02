package org.sunrise.game.gmback.server.controller;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerManagerShutdownTest {

    @Test
    void shutdownDrainsControllerEventsAndRejectsNewOnes() throws Exception {
        AtomicInteger processed = new AtomicInteger();
        CountDownLatch firstEventDone = new CountDownLatch(1);

        ControllerManager.startSaveDbPulse();
        ControllerManager.addAsyncEvent(() -> {
            processed.incrementAndGet();
            firstEventDone.countDown();
        });

        assertTrue(firstEventDone.await(2, TimeUnit.SECONDS));
        ControllerManager.prepareShutdown();

        ControllerManager.addAsyncEvent(processed::incrementAndGet);
        Thread.sleep(100);

        assertEquals(1, processed.get());
    }
}
