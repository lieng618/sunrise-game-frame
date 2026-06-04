package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.logic.monster.MonsterAi;

/**
 * 怪物场景单位。
 *
 * <p>行为逻辑由 {@link MonsterAi} 承担，本类只保存场景数据（位置、属性、存活状态等）。
 * AI 在刷怪时由 {@link org.sunrise.game.game.logic.monster.MonsterSpawner} 创建并挂载。
 */
@Getter
public class MonsterUnit implements GameUnit {
    private final String unitId;
    private final int monsterId;
    private int mapId;
    private final Position position = new Position();
    private final AttributeContainer attributeContainer = new AttributeContainer();

    /** 怪物 AI，负责巡逻等行为；由刷怪器创建，死亡后随单位丢弃 */
    @Setter
    private MonsterAi ai;
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
