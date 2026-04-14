DROP DATABASE IF EXISTS sunrise;

CREATE DATABASE sunrise;

USE sunrise;

DROP TABLE IF EXISTS external_system;

CREATE TABLE external_system
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ip          VARCHAR(255) DEFAULT '127.0.0.1'       NOT NULL COMMENT 'ip',
    port        INT          DEFAULT 10000             NOT NULL COMMENT 'port',
    status      INT          DEFAULT 0                 NOT NULL COMMENT '状态：0关闭1开启'
)
    COMMENT '对外服务';

DROP TABLE IF EXISTS rpc_server_system;
CREATE TABLE `rpc_server_system`
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ip          VARCHAR(255) DEFAULT '127.0.0.1'       NOT NULL COMMENT 'ip',
    port        INT          DEFAULT 20000             NOT NULL COMMENT 'port',
    status      INT          DEFAULT 0                 NOT NULL COMMENT '状态：0关闭1开启'
)
    COMMENT 'rpc服务';

DROP TABLE IF EXISTS account;
CREATE TABLE `account`
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    uid         VARCHAR(255) DEFAULT ''                NOT NULL COMMENT 'uid'
)
    COMMENT '账号';

DROP TABLE IF EXISTS human_list;
CREATE TABLE `human_list`
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    uid         VARCHAR(255) DEFAULT ''                NOT NULL COMMENT 'uid',
    human_id    VARCHAR(255) DEFAULT ''                NOT NULL COMMENT 'humanId',
    server_id   INT          DEFAULT 0                 NOT NULL COMMENT '服务器Id',
    pos         INT          DEFAULT 0                 NOT NULL COMMENT '位置',
    name        VARCHAR(255) DEFAULT ''                NOT NULL COMMENT '名字',
    level       INT          DEFAULT 1                 NOT NULL COMMENT '等级'
)
    COMMENT '角色列表';

DROP TABLE IF EXISTS human_info;
CREATE TABLE `human_info`
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    human_id    VARCHAR(255)                       NOT NULL COMMENT '角色ID',
    role_data   mediumblob                         NOT NULL COMMENT '玩家存档',
    UNIQUE KEY `idx_human_id` (human_id) USING BTREE
)
    COMMENT ='角色信息';

DROP TABLE IF EXISTS server_data;
CREATE TABLE `server_data`
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    server_id   INT          DEFAULT 0                 NOT NULL COMMENT '服务器Id',
    name        VARCHAR(255) DEFAULT ''                NOT NULL COMMENT '服务名字',
    data        mediumblob                             NOT NULL COMMENT '服务存档'
)
    COMMENT ='系统服务';