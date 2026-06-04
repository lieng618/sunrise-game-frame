package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.logic.map.GameMap;

import java.util.HashMap;
import java.util.Map;

/**
 * 地图系统：统一管理所有地图实例，并驱动各地图心跳。
 */
@GameSystem
public class MapSystem extends BaseSystem {
    private final Map<Integer, GameMap> maps = new HashMap<>();

    @Override
    public void init() {
        maps.clear();
        for (TbMap map : Tables.ConfigMap.getDataList()) {
            maps.put(map.id, new GameMap(map.id));
        }
    }

    @Override
    public void pulsePer100Ms() {
        for (GameMap gameMap : maps.values()) {
            gameMap.pulsePer100Ms();
        }
    }

    public GameMap getMap(int mapId) {
        return maps.get(mapId);
    }

    public void addMap(int mapId, GameMap gameMap) {
        maps.put(mapId, gameMap);
    }

    public void removeMap(int mapId) {
        maps.remove(mapId);
    }

    public Map<Integer, GameMap> getAllMaps() {
        return maps;
    }
}
