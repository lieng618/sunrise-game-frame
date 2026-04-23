# sunrise-game-frame

## 1. 项目简介

`sunrise-game-frame` 是一个基于 Java 21 + Maven 的分布式游戏服务器框架，采用“中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台 + 客户端/机器人”的多进程架构。框架不依赖任何第三方中间件，减少开发与部署的负担，只需具备 Java 环境即可运行。

项目主要特点：

- 采用分布式多节点架构，中心服管理节点，所有业务节点支持动态扩容。
- 基于自研 RPC 框架搭建多个服务模块，结构清晰，易于扩展。
- 采用单线程无锁异步化设计，避免了多线程并发问题。
- 无任何中间件依赖，业务代码编写规范，业务模块化设计。
- 实现了协议发送工具与机器人压测工具，开发测试更便捷。

主要使用技术栈:

- netty（高性能网络框架）
- java-Kcp（kcp传输协议）
- protobuf（消息序列化）
- msgpack（消息序列化）
- fastjson（json序列化）
- mysql（数据库）
- HikariCP（数据库连接池）
- slf4j（日志输出）
- yitter（分布式雪花算法）
- javalin（轻量级web框架）
- luban（游戏配置工作流）

---

## 2. 项目结构

### 2.1 顶层目录

```text
sunrise-game-frame/
├─ pom.xml                     # Maven 聚合工程
├─ config/                     # 各服务配置
├─ center/                     # 中心服启动模块
├─ network/                    # 网络层、DB、RPC、基础工具
├─ gen/                        # 协议 / RPC / DB 实体生成代码
├─ game/                       # 游戏主逻辑、多服务实现
├─ client/                     # 客户端工具、机器人
├─ admin-ui/                   # GM 后台前端页面
├─ start/                      # Windows / Linux / Docker 启动脚本
├─ tables/                     # Excel 配置表与 Luban 生成配置
└─ docs/                       # 详细的架构文档
```

### 2.2 Maven 模块 

根 `pom.xml` 中定义了 5 个子模块：

- `network`：底层公共能力
- `center`：中心服进程
- `gen`：生成代码与协议定义
- `game`：对外服 / 游戏服 / 全局服 / HTTP / GM 后台
- `client`：发送消息客户端与机器人

---

## 3. 框架架构图

![sunrise-game-frame.png](https://files.seeusercontent.com/2026/04/14/5unS/sunrise-game-frame.png)

---

## 4. 服务器启动

### 4.1 环境要求

- JDK 21
- Maven
- MySQL

### 4.2 代码编译

由于这是聚合工程，直接在根目录执行 Maven 即可：

```bash
mvn install
mvn clean package
```

执行后会生成：

- `start/jar/sunrise-center.jar`
- `start/jar/sunrise-external.jar`
- `start/jar/sunrise-game.jar`
- `start/jar/sunrise-global.jar`
- `start/jar/sunrise-http.jar`
- `start/jar/sunrise-gmback.jar`
- `start/jar/sunrise-client.jar`
- `start/jar/sunrise-bot.jar`
- `start/jar/sunrise-runallone.jar`

### 4.3 配置文件

所有服务都从 `config/` 目录读取配置：

- `center-config.properties`
- `external-config.properties`
- `game-config.properties`
- `global-config.properties`
- `http-config.properties`
- `gmback-config.properties`
- `runallone-config.properties`

其中典型项有：

- `jdbc.*` (mysql连接信息)
- `master.id/master.address/master.port` （中央服id、ip、端口）
- `report.address` （每个节点向中心服上报ip，使得所有节点互连）
- `external.address` （对外服向http服务上报ip，客户端通过http请求获取对外服地址进行连接）
- `http.address`/`http.port`（测试客户端，连接的http服务的ip、端口）
- `admin.port`（gm后台服务监听的端口）
- `admin.user/admin.password`（gm后台登录用户名、密码）
- `admin.uipath`（gm后台静态资源路径）
- `config.path`（游戏服配置表数据路径）

根据自己的环境，修改为正确配置。

### 4.4 数据库初始化

sql脚本内部会创建数据表，执行脚本前，需修改脚本内的mysql地址、账号、密码。

Windows：

```bat
start/windows/create_sql_table.bat
```

Linux：

```bash
sh start/linux/create_sql_table.sh
```

初始化 SQL 会创建以下表：

- `external_system`（对外服地址管理）
- `rpc_server_system`（rpc节点地址管理）
- `account`（玩家账号表）
- `human_list`（玩家角色列表）
- `human_info`（玩家信息存档表）
- `server_data`（服务信息存档表）

### 4.5 Windows 一键启动

服务多进程部署启动
```bat
start/windows/server_run_all.bat
```

服务单进程部署启动
```bat
start/windows/single/runallone.bat
```

### 4.6 Linux 一键启动

安装pm2
```bash
npm install -g pm2
```

服务多进程部署启动
```bash
sh start/linux/server_run_all.sh
```

服务单进程部署启动
```bash
sh start/linux/server_run_allone.sh
```

### 4.7 Docker启动

```bash
cd start/docker
docker compose up -d --build
```

### 4.8 GM后台登录

启动后访问：

```text
http://127.0.0.1:8010/
```

账号与密码为配置中所设置的：

- admin.user
- admin.password

已实现后台功能

- 节点监控
- 配置热更
- 发送邮件
- 玩家下线
- 玩家封禁
- 玩家禁言
- 操作日志
- 用户管理

---

## 5. 客户端启动

### 5.1 发送消息工具

Windows：

```bat
start/windows/client.bat
```

本质入口：

- `sendmsg.main.ClientStartUp`

### 5.2 机器人客户端

Windows：

```bat
start/windows/bot.bat
```

本质入口：

- `bot.main.BotStartUp`

### 5.3 客户端配置

客户端统一读：

- `config/client-config.properties`

典型配置：

```properties
http.address=127.0.0.1
http.port=8090
client.socket=tcp
```

可选：

- `client.socket=tcp`
- `client.socket=websocket`

---

## 6. 已有业务模块一览

目前协议与业务已覆盖：

- 登录
- 玩家信息
- 聊天
- 地图
- 背包
- 任务
- 好友
- 邮件
- 活动
- 属性

适合作为新增业务模块的参考模板。

---

## 7. 联系我

qq：1906438581

mail：lieng618@163.com
