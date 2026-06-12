package org.sunrise.game.game.logic.unit;

import lombok.Getter;

@Getter
public enum UnitType {
    PLAYER(1, "玩家"),
    MONSTER(2, "怪物"),
    NPC(3, "NPC"),
    DROP_ITEM(4, "掉落物");

    private final int value;
    private final String label;

    UnitType(int value, String label) {
        this.value = value;
        this.label = label;
    }
}
