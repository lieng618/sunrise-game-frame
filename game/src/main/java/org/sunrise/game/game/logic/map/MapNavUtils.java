package org.sunrise.game.game.logic.map;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.log.LogCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MapNavUtils {
    private MapNavUtils() {}

    private static final Map<Integer, MapNavData> navDataMap = new HashMap<>();

    public static void load() {
        navDataMap.clear();
        String path = ConfigReader.getProp().getProperty("config.nav.path", "");
        if (path.isEmpty()) {
            LogCore.GameServer.warn("config.nav.path not set, skip map nav loading");
            return;
        }
        Path navDir = Paths.get(path);
        if (!Files.isDirectory(navDir)) {
            LogCore.GameServer.warn("Map nav directory not found: {}", path);
            return;
        }

        long startTime = System.currentTimeMillis();
        int loaded = 0;
        for (TbMap map : Tables.ConfigMap.getDataList()) {
            Path file = navDir.resolve(map.id + ".nav.json");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                MapNavData navData = MapNavData.load(file);
                if (navData.getMapId() != map.id) {
                    throw new IllegalArgumentException(
                            "Nav map_id " + navData.getMapId() + " does not match file name map " + map.id);
                }
                navDataMap.put(map.id, navData);
                loaded++;
            } catch (IOException | RuntimeException e) {
                throw new RuntimeException("Failed to load map nav: " + file, e);
            }
        }
        LogCore.GameServer.info("Load map nav end, loaded {} maps, took {} ms", loaded, System.currentTimeMillis() - startTime);
    }

    public static MapNavData get(int mapId) {
        return navDataMap.get(mapId);
    }
}
