package org.sunrise.game.core.message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseMessageManagerTest {

    @Test
    void shutdownBarrierRunsOnDispatchThreadAfterAlreadyQueuedMessages() throws Exception {
        RecordingMessageManager manager = new RecordingMessageManager("shutdown-order-test");
        manager.run();
        try {
            assertTrue(manager.recvMsg("before-freeze"));
            manager.freezeRecvQueue();
            assertFalse(manager.recvMsg("after-freeze"));

            AtomicReference<Thread> barrierThread = new AtomicReference<>();
            CompletableFuture<Void> barrier = manager.submitAfterDrainOnDispatchThread(
                    () -> barrierThread.set(Thread.currentThread()));

            barrier.get(2, TimeUnit.SECONDS);

            assertEquals(List.of("before-freeze"), manager.processed);
            assertSame(manager.handlerThread.get(), barrierThread.get());
        } finally {
            assertTrue(manager.stopDispatch(2_000));
        }
    }

    @Test
    void barrierCannotBeScheduledBeforeRecvQueueIsFrozen() throws Exception {
        RecordingMessageManager manager = new RecordingMessageManager("freeze-required-test");
        manager.run();
        try {
            CompletableFuture<Void> barrier =
                    manager.submitAfterDrainOnDispatchThread(() -> { });

            Exception error = org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> barrier.get(2, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof IllegalStateException);
        } finally {
            assertTrue(manager.stopDispatch(2_000));
        }
    }

    @Test
    void onlyOneBarrierCanBeScheduledAfterFreeze() throws Exception {
        RecordingMessageManager manager = new RecordingMessageManager("single-barrier-test");
        manager.run();
        try {
            manager.freezeRecvQueue();
            manager.submitAfterDrainOnDispatchThread(() -> { }).get(2, TimeUnit.SECONDS);

            java.util.concurrent.CompletableFuture<Void> second =
                    manager.submitAfterDrainOnDispatchThread(() -> { });

            java.util.concurrent.ExecutionException error =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> second.get(2, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof IllegalStateException);
        } finally {
            assertTrue(manager.stopDispatch(2_000));
        }
    }

    private static final class RecordingMessageManager extends BaseMessageManager {
        private final List<Object> processed = new ArrayList<>();
        private final AtomicReference<Thread> handlerThread = new AtomicReference<>();

        private RecordingMessageManager(String nodeId) {
            super(nodeId);
        }

        @Override
        protected void pulseHandlerOne(Object data) {
            handlerThread.set(Thread.currentThread());
            processed.add(data);
        }

        @Override
        protected void pulseSenderOne(Object data) {
        }
    }
}
