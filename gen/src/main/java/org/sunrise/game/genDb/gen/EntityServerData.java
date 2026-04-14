package org.sunrise.game.genDb.gen;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityServerData {
    private final int id;
    private final LocalDateTime create_time;
    private final LocalDateTime update_time;
    private final int server_id;
    private final String name;
    private final byte[] data;

    public EntityServerData(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.update_time = (LocalDateTime) dataMap.get("update_time");
        this.server_id = (int) dataMap.get("server_id");
        this.name = (String) dataMap.get("name");
        this.data = (byte[]) dataMap.get("data");
    }
}
