package org.sunrise.game.game.modules;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.param.ConfigParam;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModuleTest {

    private static ConfigParam originalConfig;

    @BeforeAll
    static void configureTables() {
        originalConfig = Tables.ConfigParam;
        Tables.ConfigParam = new ConfigParam(JsonParser.parseString("""
                [
                  {
                    "ItemBoxCapacity": 2,
                    "ItemMaxStack": 5,
                    "TaskInitIds": [],
                    "MapInitId": 10001,
                    "DefaultHeadIcon": 1001
                  }
                ]
                """));
    }

    @AfterAll
    static void restoreTables() {
        Tables.ConfigParam = originalConfig;
    }

    @Test
    void batchAddRejectsWholeBatchWhenSlotsAreInsufficient() {
        ItemModule itemModule = new ItemModule("test-human");
        assertTrue(itemModule.tryAddItems(Map.of(1001, 5L), false));

        assertFalse(itemModule.tryAddItems(Map.of(1002, 1L, 1003, 1L), false));

        assertEquals(1, itemModule.getItemsList().size());
        assertEquals(1001, itemModule.getItemsList().getFirst().getItemId());
        assertEquals(5, itemModule.getItemsList().getFirst().getCount());
    }

    @Test
    void batchAddUsesExistingStackSpaceBeforeNewSlots() {
        ItemModule itemModule = new ItemModule("test-human");
        assertTrue(itemModule.tryAddItems(Map.of(1001, 6L), false));

        assertTrue(itemModule.tryAddItems(Map.of(1001, 4L), false));

        assertEquals(2, itemModule.getItemsList().size());
        assertEquals(10, itemModule.getItemsList().stream()
                .mapToInt(item -> item.getCount())
                .sum());
    }
}
