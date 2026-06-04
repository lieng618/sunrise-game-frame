package org.sunrise.game.game.logic.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 地图可走性格子数据（Godot 导出的 nav.json）
 * cells: '0' 可走，'1' 阻挡；index = y * width + x
 */
@Getter
public class MapNavData {
    private final int mapId;
    private final int tileSize;
    private final int width;
    private final int height;
    private final char[] cells;

    private MapNavData(int mapId, int tileSize, int width, int height, char[] cells) {
        this.mapId = mapId;
        this.tileSize = tileSize;
        this.width = width;
        this.height = height;
        this.cells = cells;
    }

    public static MapNavData load(Path path) throws IOException {
        JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        int mapId = obj.get("map_id").getAsInt();
        int tileSize = obj.get("tile_size").getAsInt();
        int width = obj.get("width").getAsInt();
        int height = obj.get("height").getAsInt();
        String cellsStr = obj.get("cells").getAsString();
        if (tileSize <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid nav dimensions in " + path);
        }
        int expectedLen = width * height;
        if (cellsStr.length() != expectedLen) {
            throw new IllegalArgumentException(
                    "Nav cells length mismatch in " + path + ", expected " + expectedLen + ", got " + cellsStr.length());
        }
        return new MapNavData(mapId, tileSize, width, height, cellsStr.toCharArray());
    }

    /**
     * 世界坐标对应格子是否阻挡（越界视为阻挡）
     */
    public boolean isBlocked(float worldX, float worldY) {
        int tileX = (int) Math.floor(worldX / tileSize);
        int tileY = (int) Math.floor(worldY / tileSize);
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
            return true;
        }
        return cells[tileY * width + tileX] == '1';
    }

    public boolean isWalkable(float worldX, float worldY) {
        return !isBlocked(worldX, worldY);
    }
}
