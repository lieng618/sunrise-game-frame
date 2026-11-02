package org.sunrise.game.game.logic.unit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneUnitIdTest {

    @Test
    void monstersAndDropsShareOneIdSequence() {
        Set<String> unitIds = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            unitIds.add(new MonsterUnit(1).getUnitId());
            unitIds.add(new DropItemUnit(1, 1).getUnitId());
        }

        assertEquals(200, unitIds.size());
    }
}
