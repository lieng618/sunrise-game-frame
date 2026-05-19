package org.sunrise.game.genDb.gen;

import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 数据库表 human_list 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityHumanList {
    int id;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    String uid;
    String humanId;
    int serverId;
    int pos;
    String name;
    int level;

    public EntityHumanList(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.createTime = EntityConverter.convertToType(dataMap.get("create_time"), LocalDateTime.class);
        this.updateTime = EntityConverter.convertToType(dataMap.get("update_time"), LocalDateTime.class);
        this.uid = EntityConverter.convertToType(dataMap.get("uid"), String.class);
        this.humanId = EntityConverter.convertToType(dataMap.get("human_id"), String.class);
        this.serverId = EntityConverter.convertToType(dataMap.get("server_id"), int.class);
        this.pos = EntityConverter.convertToType(dataMap.get("pos"), int.class);
        this.name = EntityConverter.convertToType(dataMap.get("name"), String.class);
        this.level = EntityConverter.convertToType(dataMap.get("level"), int.class);
    }
}
