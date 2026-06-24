package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monsterRefresh.TbMonsterRefresh;
import org.sunrise.game.game.logic.monster.MonsterSpawner;
import org.sunrise.game.log.LogCore;

import java.util.HashMap;
import java.util.Map;

/**
 * 怪物系统：按刷新表驱动各地图刷怪与复活。
 */
@GameSystem
public class MonsterSystem extends BaseSystem {
    private final Map<Integer, MonsterSpawner> spawners = new HashMap<>();

    @Override
    public void init() {
        for (TbMonsterRefresh cfg : Tables.ConfigMonsterRefresh.getDataList()) {
            if (Tables.ConfigMonster.get(cfg.monsterId) == null) {
                LogCore.GameServer.warn("MonsterSystem skip refresh, monster template not found, refreshId={}, monsterId={}",
                        cfg.id, cfg.monsterId);
                continue;
            }
            spawners.put(cfg.id, new MonsterSpawner(cfg.id));
        }
    }

    @Override
    public void pulsePerSec() {
        for (MonsterSpawner spawner : spawners.values()) {
            spawner.tickRespawn();
        }
    }
}
