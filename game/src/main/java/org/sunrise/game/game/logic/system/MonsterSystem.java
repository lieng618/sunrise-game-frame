package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.UnitUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 怪物刷怪与复活系统
 */
@GameSystem
public class MonsterSystem extends BaseSystem {
    private final Map<Integer, MonsterSpawner> spawners = new HashMap<>();

    @Override
    public void init() {
        for (TbMonster cfg : Tables.ConfigMonster.getDataList()) {
            MonsterSpawner spawner = new MonsterSpawner(cfg.id, cfg.mapId);
            spawners.put(cfg.id, spawner);
        }
    }

    @Override
    public void pulsePerSec() {
        for (MonsterSpawner spawner : spawners.values()) {
            spawner.tickRespawn();
        }
    }

    public static class MonsterSpawner {
        private final int monsterId;
        private final int mapId;
        private MonsterUnit current;

        public MonsterSpawner(int monsterId, int mapId) {
            this.monsterId = monsterId;
            this.mapId = mapId;
        }

        private GameMap getGameMap() {
            return GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
        }

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

        public void tickRespawn() {
            TbMonster cfg = Tables.ConfigMonster.get(monsterId);
            if (cfg == null) {
                return;
            }
            if (current != null) {
                // 从未死亡或者未到刷新时间
                if (current.getDeadTime() == 0 || System.currentTimeMillis() - current.getDeadTime() < cfg.respawnTime * 1000L) {
                    return;
                }
            }
            GameMap gameMap = getGameMap();
            if (gameMap == null) {
                return;
            }
            current = new MonsterUnit(monsterId);
            UnitUtils.initMonsterAttributes(current);
            current.getPosition().set(cfg.spawnX, cfg.spawnY, cfg.spawnZ, 0);
            current.setMapId(mapId);
            current.setAlive(true);
            gameMap.enterUnit(current);
        }
    }
}
