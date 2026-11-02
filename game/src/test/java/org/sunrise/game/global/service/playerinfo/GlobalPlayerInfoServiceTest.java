package org.sunrise.game.global.service.playerinfo;

import org.junit.jupiter.api.Test;
import org.sunrise.game.game.logic.playerinfo.PlayerInfo;

import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalPlayerInfoServiceTest {

    @Test
    void paginatesMoreThanTenThousandPlayersByOpaqueCursor() {
        NavigableMap<String, PlayerInfo> playerInfos = new ConcurrentSkipListMap<>();
        int playerCount = 10_001;
        for (int i = 0; i < playerCount; i++) {
            String humanId = "player-" + i;
            playerInfos.put(humanId, new PlayerInfo());
        }

        Set<String> received = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            GlobalPlayerInfoService.PlayerIdPage page =
                    GlobalPlayerInfoService.selectPlayerIdPage(playerInfos, cursor, 1_000);

            assertTrue(page.humanIds().size() <= 1_000);
            received.addAll(page.humanIds());
            pages++;

            if (page.hasMore()) {
                assertNotNull(page.nextCursor());
                assertEquals(page.humanIds().getLast(), page.nextCursor());
            } else {
                assertNull(page.nextCursor());
            }
            cursor = page.nextCursor();
        } while (cursor != null);

        assertEquals(playerCount, received.size());
        assertEquals(11, pages);
    }

    @Test
    void clampsRequestedPageSizeToRpcCollectionLimit() {
        NavigableMap<String, PlayerInfo> playerInfos = new ConcurrentSkipListMap<>();
        for (int i = 0; i < 10_001; i++) {
            playerInfos.put("player-" + i, new PlayerInfo());
        }

        GlobalPlayerInfoService.PlayerIdPage page =
                GlobalPlayerInfoService.selectPlayerIdPage(playerInfos, null, 20_000);

        assertEquals(10_000, page.humanIds().size());
        assertTrue(page.hasMore());
        assertTrue(page.humanIds().contains(page.nextCursor()));
    }
}
