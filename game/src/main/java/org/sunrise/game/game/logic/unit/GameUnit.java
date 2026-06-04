package org.sunrise.game.game.logic.unit;

import org.sunrise.game.game.logic.attribute.AttributeContainer;

public interface GameUnit {
    String getUnitId();

    UnitType getUnitType();

    int getMapId();

    void setMapId(int mapId);

    AttributeContainer getAttributeContainer();

    Position getPosition();

    /** 怪物/NPC 配置 id，玩家为 0 */
    int getConfigId();
}
