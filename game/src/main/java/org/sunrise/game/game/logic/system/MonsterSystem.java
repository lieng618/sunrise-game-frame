package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.logic.monster.MonsterSpawner;

import java.util.HashMap;
import java.util.Map;

/**
 * 怪物系统：负责刷怪与复活。
 */
@GameSystem
public class MonsterSystem extends BaseSystem {
    private final Map<Integer, MonsterSpawner> spawners = new HashMap<>();

    @Override
    public void init() {
        for (TbMonster cfg : Tables.ConfigMonster.getDataList()) {
            spawners.put(cfg.id, new MonsterSpawner(cfg.id, cfg.mapId));
        }
    }

    @Override
    public void pulsePerSec() {
        for (MonsterSpawner spawner : spawners.values()) {
            spawner.tickRespawn();
        }
    }
}
