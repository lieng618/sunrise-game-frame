package org.sunrise.game.db.entity;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据库表 human_info 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityHumanInfo {
    int id;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    String humanId;
    byte[] roleData;

    public EntityHumanInfo(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.createTime = EntityConverter.convertToType(dataMap.get("create_time"), LocalDateTime.class);
        this.updateTime = EntityConverter.convertToType(dataMap.get("update_time"), LocalDateTime.class);
        this.humanId = EntityConverter.convertToType(dataMap.get("human_id"), String.class);
        this.roleData = EntityConverter.convertToType(dataMap.get("role_data"), byte[].class);
    }
}
