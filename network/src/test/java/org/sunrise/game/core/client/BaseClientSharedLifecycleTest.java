package org.sunrise.game.core.client;

import io.netty.channel.DefaultEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.sunrise.game.core.message.ClientMessageManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseClientSharedLifecycleTest {

    @Test
    void closingOneClientDoesNotStopSharedGroupOrMessageManager() {
        DefaultEventLoopGroup sharedGroup = new DefaultEventLoopGroup(1);
        ClientMessageManager sharedManager = new ClientMessageManager("shared-manager");
        BaseClient first = new BaseClient("peer-a", sharedGroup, null);
        BaseClient second = new BaseClient("peer-b", sharedGroup, null);
        first.useSharedMessageManager(sharedManager);
        second.useSharedMessageManager(sharedManager);

        try {
            first.disconnect();

            assertFalse(sharedGroup.isShuttingDown());
            assertTrue(sharedManager.isAcceptingRecvMessages());

            first.onStop();

            assertFalse(sharedGroup.isShuttingDown());
            assertTrue(sharedManager.isAcceptingRecvMessages());
        } finally {
            second.onStop();
            sharedGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
