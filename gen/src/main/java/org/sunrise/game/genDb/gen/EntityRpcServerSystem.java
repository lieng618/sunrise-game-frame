package org.sunrise.game.genDb.gen;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityRpcServerSystem {
    private final int id;
    private final LocalDateTime create_time;
    private final LocalDateTime update_time;
    private final String ip;
    private final int port;
    private final int status;

    public EntityRpcServerSystem(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.update_time = (LocalDateTime) dataMap.get("update_time");
        this.ip = (String) dataMap.get("ip");
        this.port = (int) dataMap.get("port");
        this.status = (int) dataMap.get("status");
    }
}
