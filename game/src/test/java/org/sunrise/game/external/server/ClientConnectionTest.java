package org.sunrise.game.external.server;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConnectionTest {
    private static final byte[] MESSAGE = {1};

    @Test
    void rejectsMessagesAfterBurstIsExhaustedWithoutResettingTheBucket() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ClientConnection connection = new ClientConnection(channel);

        try {
            for (int i = 0; i < 120; i++) {
                assertTrue(connection.dataCheck(MESSAGE));
            }
            assertFalse(connection.dataCheck(MESSAGE));
            assertFalse(connection.dataCheck(MESSAGE));
            assertTrue(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closesConnectionAfterRepeatedRateLimitViolations() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ClientConnection connection = new ClientConnection(channel);

        try {
            for (int i = 0; i < 120; i++) {
                assertTrue(connection.dataCheck(MESSAGE));
            }
            for (int i = 0; i < 600; i++) {
                assertFalse(connection.dataCheck(MESSAGE));
            }
            channel.runPendingTasks();

            assertFalse(channel.isActive());
            assertFalse(connection.dataCheck(MESSAGE));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
