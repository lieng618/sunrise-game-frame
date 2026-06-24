package org.sunrise.game.game.logic.monster;

import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.config.monsterRefresh.TbMonsterRefresh;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.UnitUtils;

/**
 * 单个刷新点的刷怪与复活，对应 {@link TbMonsterRefresh} 一行配置。
 *
 * <p>怪物模板属性来自 {@link TbMonster}，出生位置与复活时间来自刷新表。
 * 与 {@link MonsterAi} 的分工：本类负责单位创建、进入地图；AI 在 {@link #spawn} 中挂载。
 */
public class MonsterSpawner {
    private final int refreshId;
    private MonsterUnit current;

    public MonsterSpawner(int refreshId) {
        this.refreshId = refreshId;
    }

    /** 每秒检查一次是否满足复活条件，满足则重新刷怪 */
    public void tickRespawn() {
        TbMonsterRefresh refresh = getRefreshCfg();
        if (refresh == null) {
            return;
        }
        if (current != null && !canRespawn(refresh)) {
            return;
        }
        GameMap gameMap = getGameMap(refresh.mapId);
        if (gameMap == null) {
            return;
        }
        spawn(refresh, gameMap);
    }

    private boolean canRespawn(TbMonsterRefresh refresh) {
        if (current.getDeadTime() == 0) {
            return false;
        }
        return System.currentTimeMillis() - current.getDeadTime() >= refresh.respawnTime * 1000L;
    }

    /**
     * 创建怪物实例、挂载 AI 并加入地图。
     */
    private void spawn(TbMonsterRefresh refresh, GameMap gameMap) {
        TbMonster monsterCfg = Tables.ConfigMonster.get(refresh.monsterId);
        if (monsterCfg == null) {
            return;
        }
        current = new MonsterUnit(refresh.monsterId);
        UnitUtils.initMonsterAttributes(current);
        current.getPosition().set(refresh.spawnX, refresh.spawnY, refresh.spawnZ, 0);
        current.setMapId(refresh.mapId);
        current.setAlive(true);
        current.setDeadTime(0L);
        current.setAi(new MonsterAi(current, refresh.mapId, refresh.spawnX, refresh.spawnY));
        gameMap.enterUnit(current);
    }

    private TbMonsterRefresh getRefreshCfg() {
        return Tables.ConfigMonsterRefresh.get(refreshId);
    }

    private GameMap getGameMap(int mapId) {
        return GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
    }
}
