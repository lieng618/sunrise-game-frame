package org.sunrise.game.genDb.gen;

import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 数据库表 server_data 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityServerData {
    int id;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    int serverId;
    String name;
    byte[] data;

    public EntityServerData(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.createTime = EntityConverter.convertToType(dataMap.get("create_time"), LocalDateTime.class);
        this.updateTime = EntityConverter.convertToType(dataMap.get("update_time"), LocalDateTime.class);
        this.serverId = EntityConverter.convertToType(dataMap.get("server_id"), int.class);
        this.name = EntityConverter.convertToType(dataMap.get("name"), String.class);
        this.data = EntityConverter.convertToType(dataMap.get("data"), byte[].class);
    }
}
