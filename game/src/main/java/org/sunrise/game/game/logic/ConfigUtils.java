package org.sunrise.game.game.logic;

import com.google.gson.JsonParser;
import lombok.Getter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.log.LogCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public class ConfigUtils {
    private ConfigUtils() {}
    private static volatile Tables currentTables;

    public static void load() {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 加载构建新实例
            Path configDir = getConfigFilePath();
            Tables newTables = new Tables(file -> JsonParser.parseString(Files.readString(configDir.resolve(file + ".json"))));

            // 2. 更新本地持有 (Root引用)
            currentTables = newTables;

            // 3. 注入到 Tables 的静态字段中 (对外暴露)
            Tables.load(newTables);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LogCore.GameServer.info("Load config end, took {} ms", System.currentTimeMillis() - startTime);
    }

    /**
     * 获取配置目录
     */
    private static Path getConfigFilePath() {
        String path = ConfigReader.getProp().getProperty("config.path");
        if (path == null || path.isEmpty()) {
            LogCore.GameServer.error("Load config failed,  config path not found");
            System.exit(-1);
        }
        return ConfigReader.resolvePath(path);
    }
}
