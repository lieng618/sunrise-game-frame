package org.sunrise.game.db.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityHumanInfo {
    private final int id;
    private final LocalDateTime create_time;
    private final LocalDateTime update_time;
    private final String human_id;
    private final byte[] role_data;

    public EntityHumanInfo(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.update_time = (LocalDateTime) dataMap.get("update_time");
        this.human_id = (String) dataMap.get("human_id");
        this.role_data = (byte[]) dataMap.get("role_data");
    }
}
