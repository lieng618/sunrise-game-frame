package org.sunrise.game.genDb.gen;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EntityGmOperationLog {
    private final int id;
    private final LocalDateTime create_time;
    private final String operator;
    private final String ip;
    private final String operationType;

    public EntityGmOperationLog(Map<String, Object> dataMap) {
        this.id = (int) dataMap.get("id");
        this.create_time = (LocalDateTime) dataMap.get("create_time");
        this.operator = (String) dataMap.get("operator");
        this.ip = (String) dataMap.get("ip");
        this.operationType = (String) dataMap.get("operationType");
    }
}
