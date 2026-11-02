package org.sunrise.game.graceful;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sunrise.game.core.message.BaseMessageManager;
import org.sunrise.game.rpc.function.RpcFunction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownTest {

    @BeforeEach
    void resetShutdownState() throws Exception {
        resetState();
    }

    @AfterEach
    void restoreShutdownState() throws Exception {
        resetState();
    }

    @Test
    void businessPhaseRunsOnRegisteredDispatchThread() throws Exception {
        TestMessageManager manager = new TestMessageManager("graceful-test");
        manager.run();
        manager.recvMsg("dispatch-thread-probe");
        manager.freezeRecvQueue();
        GracefulShutdown.registerBusinessMessageManager(manager);

        AtomicReference<Thread> businessThread = new AtomicReference<>();
        AtomicReference<Thread> controlThread = new AtomicReference<>();
        addPhase(ShutdownPhase.FINAL_BUSINESS_STATE, "business", 1_000,
                () -> businessThread.set(Thread.currentThread()));
        addPhase(ShutdownPhase.DRAIN_RPC, "control", 1_000,
                () -> controlThread.set(Thread.currentThread()));

        try {
            GracefulShutdown.trigger();

            assertTrue(GracefulShutdown.isCompleted());
            assertSame(manager.handlerThread.get(), businessThread.get());
            assertFalse(controlThread.get() == Thread.currentThread());
        } finally {
            manager.stopDispatch(2_000);
        }
    }

    @Test
    void timeoutFailsShutdownAndSkipsLaterResourcePhases() throws Exception {
        AtomicInteger laterPhaseRuns = new AtomicInteger();
        addPhase(ShutdownPhase.DRAIN_RPC, "slow", 50, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        addPhase(ShutdownPhase.CLOSE_DB, "must-be-skipped", 1_000,
                laterPhaseRuns::incrementAndGet);

        GracefulShutdown.trigger();

        assertFalse(GracefulShutdown.isCompleted());
        assertTrue(GracefulShutdown.getFailure() instanceof TimeoutException);
        assertEquals(0, laterPhaseRuns.get());
    }

    @Test
    void phasesRunInLifecycleOrderRegardlessOfRegistrationOrder() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        addPhase(ShutdownPhase.CLOSE_DB, "close-db", 1_000,
                () -> order.add("close-db"));
        addPhase(ShutdownPhase.DRAIN_RPC, "drain-rpc", 1_000,
                () -> order.add("drain-rpc"));
        addPhase(ShutdownPhase.STOP_INPUT, "stop-input", 1_000,
                () -> order.add("stop-input"));

        GracefulShutdown.trigger();

        assertEquals(List.of("drain-rpc", "stop-input", "close-db"), order);
    }

    @SuppressWarnings("unchecked")
    private static void resetState() throws Exception {
        Field phasesField = GracefulShutdown.class.getDeclaredField("phases");
        phasesField.setAccessible(true);
        ((List<Object>) phasesField.get(null)).clear();

        resetAtomicBoolean("triggered");
        resetAtomicBoolean("completed");

        Field failureField = GracefulShutdown.class.getDeclaredField("failure");
        failureField.setAccessible(true);
        ((AtomicReference<Throwable>) failureField.get(null)).set(null);

        Field managerField = GracefulShutdown.class.getDeclaredField("businessMessageManager");
        managerField.setAccessible(true);
        managerField.set(null, null);
        RpcFunction.shuttingDown = false;
    }

    private static void resetAtomicBoolean(String name) throws Exception {
        Field field = GracefulShutdown.class.getDeclaredField(name);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
    }

    private static void addPhase(
            ShutdownPhase phaseType, String name, long timeoutMs, Runnable task) throws Exception {
        Object phase = new GracefulShutdown.Phase(phaseType, name, task, timeoutMs);

        Field phasesField = GracefulShutdown.class.getDeclaredField("phases");
        phasesField.setAccessible(true);
        ((List<Object>) phasesField.get(null)).add(phase);
    }

    private static final class TestMessageManager extends BaseMessageManager {
        private final AtomicReference<Thread> handlerThread = new AtomicReference<>();

        private TestMessageManager(String nodeId) {
            super(nodeId);
        }

        @Override
        protected void pulseHandlerOne(Object data) {
            handlerThread.set(Thread.currentThread());
        }

        @Override
        protected void pulseSenderOne(Object data) {
        }
    }
}
