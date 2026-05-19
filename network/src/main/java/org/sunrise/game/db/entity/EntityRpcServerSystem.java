package org.sunrise.game.db.entity;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据库表 rpc_server_system 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityRpcServerSystem {
    int id;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    String ip;
    int port;
    int status;

    public EntityRpcServerSystem(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.createTime = EntityConverter.convertToType(dataMap.get("create_time"), LocalDateTime.class);
        this.updateTime = EntityConverter.convertToType(dataMap.get("update_time"), LocalDateTime.class);
        this.ip = EntityConverter.convertToType(dataMap.get("ip"), String.class);
        this.port = EntityConverter.convertToType(dataMap.get("port"), int.class);
        this.status = EntityConverter.convertToType(dataMap.get("status"), int.class);
    }
}
