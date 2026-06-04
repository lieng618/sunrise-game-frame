package org.sunrise.game.game.logic.monster;

import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.UnitUtils;

/**
 * 单个怪物配置点的刷怪与复活。
 *
 * <p>与 {@link MonsterAi} 的分工：本类负责单位创建、属性初始化、进入/离开地图；
 * AI 的创建在 {@link #spawn} 中完成，之后由 {@link GameMap#pulsePer100Ms()} 驱动。
 */
public class MonsterSpawner {
    private final int monsterId;
    private final int mapId;
    private MonsterUnit current;

    public MonsterSpawner(int monsterId, int mapId) {
        this.monsterId = monsterId;
        this.mapId = mapId;
    }

    /** 怪物死亡：标记状态并从地图移除单位（AI 随单位一起失效） */
    public void onDead() {
        if (current == null) {
            return;
        }
        current.setAlive(false);
        current.setDeadTime(System.currentTimeMillis());
        GameMap gameMap = getGameMap();
        if (gameMap != null) {
            gameMap.leaveUnit(current.getUnitId());
        }
    }

    /** 每秒检查一次是否满足复活条件，满足则重新刷怪 */
    public void tickRespawn() {
        TbMonster cfg = Tables.ConfigMonster.get(monsterId);
        if (cfg == null) {
            return;
        }
        if (current != null && !canRespawn(cfg)) {
            return;
        }
        GameMap gameMap = getGameMap();
        if (gameMap == null) {
            return;
        }
        spawn(cfg, gameMap);
    }

    private boolean canRespawn(TbMonster cfg) {
        if (current.getDeadTime() == 0) {
            return false;
        }
        return System.currentTimeMillis() - current.getDeadTime() >= cfg.respawnTime * 1000L;
    }

    /**
     * 创建怪物实例、挂载 AI 并加入地图。
     *
     * <p>顺序：新建 {@link MonsterUnit} → 初始化属性与出生坐标 → 创建 {@link MonsterAi}
     *（传入 mapId 与出生点作为巡逻锚点）→ {@link GameMap#enterUnit} 广播进场。
     */
    private void spawn(TbMonster cfg, GameMap gameMap) {
        current = new MonsterUnit(monsterId);
        UnitUtils.initMonsterAttributes(current);
        current.getPosition().set(cfg.spawnX, cfg.spawnY, cfg.spawnZ, 0);
        current.setMapId(mapId);
        current.setAlive(true);
        current.setAi(new MonsterAi(current, mapId, cfg.spawnX, cfg.spawnY));
        gameMap.enterUnit(current);
    }

    private GameMap getGameMap() {
        return GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
    }
}
