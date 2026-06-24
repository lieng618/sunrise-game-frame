package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.logic.drop.DropSystem;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.monster.MonsterAi;
import org.sunrise.game.game.logic.monster.MonsterSpawner;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;

/**
 * 怪物场景单位。
 *
 * <p>行为逻辑由 {@link MonsterAi} 承担，本类保存场景数据（位置、属性、存活状态等）。
 * AI 在刷怪时由 {@link MonsterSpawner} 创建并挂载。
 */
@Getter
public class MonsterUnit implements GameUnit {
    private final String unitId;
    /** 怪物模板 id，对应 {@link TbMonster#id} */
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

    /**
     * 怪物死亡：掉落、标记状态并从地图移除（向同图玩家广播离场）。
     */
    public void die(String killerHumanId) {
        if (!alive) {
            return;
        }
        alive = false;
        deadTime = System.currentTimeMillis();

        GameMap gameMap = GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
        if (gameMap == null) {
            return;
        }
        TbMonster cfg = Tables.ConfigMonster.get(monsterId);
        if (cfg != null && cfg.dropId > 0) {
            DropSystem dropSystem = GameSystemUtils.getSystem(DropSystem.class);
            if (dropSystem != null) {
                dropSystem.generateDrops(cfg.dropId, mapId,
                        position.getX(), position.getY(), position.getZ(), killerHumanId);
            }
        }
        gameMap.leaveUnit(unitId);
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
