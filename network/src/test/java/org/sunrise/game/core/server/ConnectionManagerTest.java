package org.sunrise.game.core.server;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionManagerTest {

    @Test
    void duplicateRegistrationDoesNotReplaceOriginalConnection() {
        String nodeId = "duplicate-test-" + UUID.randomUUID();
        EmbeddedChannel original = new EmbeddedChannel();
        EmbeddedChannel duplicate = new EmbeddedChannel();

        try {
            assertTrue(ConnectionManager.createConnect(nodeId, original));
            assertFalse(ConnectionManager.createConnect(nodeId, duplicate));
            assertSame(original, ConnectionManager.getConnect(nodeId).getChannel());
        } finally {
            ConnectionManager.removeConnect(nodeId, original);
            original.finishAndReleaseAll();
            duplicate.finishAndReleaseAll();
        }
    }

    @Test
    void conditionalRemoveRejectsAnotherChannelForSameNode() {
        String nodeId = "remove-test-" + UUID.randomUUID();
        EmbeddedChannel original = new EmbeddedChannel();
        EmbeddedChannel other = new EmbeddedChannel();

        try {
            assertTrue(ConnectionManager.createConnect(nodeId, original));

            assertFalse(ConnectionManager.removeConnect(nodeId, other));
            assertSame(original, ConnectionManager.getConnect(nodeId).getChannel());
            assertTrue(original.isActive());

            assertTrue(ConnectionManager.removeConnect(nodeId, original));
            assertNull(ConnectionManager.getConnect(nodeId));
        } finally {
            ConnectionManager.removeConnect(nodeId, original);
            original.finishAndReleaseAll();
            other.finishAndReleaseAll();
        }
    }

    @Test
    void duplicateHandshakeDoesNotDisconnectOriginalConnection() {
        String nodeId = "handshake-test-" + UUID.randomUUID();
        EmbeddedChannel original = new EmbeddedChannel();
        BaseServerHandler duplicateHandler = new BaseServerHandler("server-node");
        EmbeddedChannel duplicate = new EmbeddedChannel(duplicateHandler);

        try {
            assertTrue(ConnectionManager.createConnect(nodeId, original));

            byte[] handshake = (Utils.CLIENT_CONNECT + nodeId).getBytes(StandardCharsets.UTF_8);
            duplicate.writeInbound(new SocketMessage(MessageType.biz, handshake));
            duplicate.runPendingTasks();

            assertNull(duplicateHandler.getConnectNode());
            assertSame(original, ConnectionManager.getConnect(nodeId).getChannel());
            assertTrue(original.isActive());
        } finally {
            ConnectionManager.removeConnect(nodeId, original);
            original.finishAndReleaseAll();
            duplicate.finishAndReleaseAll();
        }
    }
}
