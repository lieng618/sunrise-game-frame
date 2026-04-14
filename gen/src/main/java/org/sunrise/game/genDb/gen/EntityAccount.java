package org.sunrise.game.genDb.gen;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityAccount {
    private final int id;
    private final LocalDateTime create_time;
    private final LocalDateTime update_time;
    private final String uid;

    public EntityAccount(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.update_time = (LocalDateTime) dataMap.get("update_time");
        this.uid = (String) dataMap.get("uid");
    }
}
