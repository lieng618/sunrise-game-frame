package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.logic.map.GameMap;

import java.util.HashMap;
import java.util.Map;

/**
 * 地图系统
 * 统一管理所有地图实例
 */
public class MapSystem extends BaseSystem {
    private final Map<Integer, GameMap> maps = new HashMap<>();

    @Override
    public void init() {
        maps.clear();
        // 初始化地图，从配置表读取地图列表
        for (TbMap map : Tables.ConfigMap.getDataList()) {
            GameMap gameMap = new GameMap(map.id);
            maps.put(map.id, gameMap);
        }
    }

    /**
     * 根据地图ID获取对应的 GameMap
     */
    public GameMap getMap(int mapId) {
        return maps.get(mapId);
    }

    /**
     * 添加地图
     */
    public void addMap(int mapId, GameMap gameMap) {
        maps.put(mapId, gameMap);
    }

    /**
     * 移除地图
     */
    public void removeMap(int mapId) {
        maps.remove(mapId);
    }

    /**
     * 获取所有地图
     */
    public Map<Integer, GameMap> getAllMaps() {
        return maps;
    }
}
