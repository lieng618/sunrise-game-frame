package org.sunrise.game.genDb.gen;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityHumanList {
    private final int id;
    private final LocalDateTime create_time;
    private final LocalDateTime update_time;
    private final String uid;
    private final String human_id;
    private final int server_id;
    private final int pos;
    private final String name;
    private final int level;

    public EntityHumanList(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.update_time = (LocalDateTime) dataMap.get("update_time");
        this.uid = (String) dataMap.get("uid");
        this.human_id = (String) dataMap.get("human_id");
        this.server_id = (int) dataMap.get("server_id");
        this.pos = (int) dataMap.get("pos");
        this.name = (String) dataMap.get("name");
        this.level = (int) dataMap.get("level");
    }
}
