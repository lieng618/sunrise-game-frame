package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.logic.attribute.AttributeContainer;

/**
 * 怪物场景单位
 */
@Getter
public class MonsterUnit implements GameUnit {
    private final String unitId;
    private final int monsterId;
    private int mapId;
    private final Position position = new Position();
    private final AttributeContainer attributeContainer = new AttributeContainer();
    @Setter
    private boolean alive = true;
    @Setter
    private long deadTime = 0L;

    public MonsterUnit(int monsterId) {
        this.monsterId = monsterId;
        this.unitId = String.valueOf(UnitUtils.genMonsterUnitId());
    }

    @Override
    public String getUnitId() {
        return unitId;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.MONSTER;
    }

    @Override
    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    @Override
    public AttributeContainer getAttributeContainer() {
        return attributeContainer;
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public int getConfigId() {
        return monsterId;
    }
}
