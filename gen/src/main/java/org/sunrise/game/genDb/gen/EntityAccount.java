package org.sunrise.game.genDb.gen;

import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 数据库表 account 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityAccount {
    int id;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    String uid;

    public EntityAccount(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.createTime = EntityConverter.convertToType(dataMap.get("create_time"), LocalDateTime.class);
        this.updateTime = EntityConverter.convertToType(dataMap.get("update_time"), LocalDateTime.class);
        this.uid = EntityConverter.convertToType(dataMap.get("uid"), String.class);
    }
}
