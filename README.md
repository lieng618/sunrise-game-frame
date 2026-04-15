# sunrise-game-frame

## 1. 项目简介

`sunrise-game-frame` 是一个基于 Java 21 + Maven 的分布式游戏服务器框架，采用“中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台 + 客户端/机器人”的多进程架构。框架不依赖任何第三方中间件，减少开发与部署的负担，只需具备 Java 环境即可运行。

项目主要特点：

- 基于 `Netty` 实现 TCP / WebSocket 通讯。
- 基于 `Protocol Buffers` 组织客户端协议。
- 基于自研 RPC 框架实现服务间通信与广播。
- 基于 `Javalin` 实现 HTTP 地址分发服务与 GM 后台。
- 基于 `MySQL + HikariCP` 实现数据持久化。
- 游戏服采用“玩家对象 + 模块化存档 + 注解路由”的业务组织方式。
- 实现了两个客户端：
  - `sunrise-client`：协议发送工具
  - `sunrise-bot`：机器人压测工具

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
├─ start/                      # Windows / Linux 启动脚本
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

## 4. MVN 编译构建

### 4.1 环境要求

- JDK 21
- Maven 3.8+
- MySQL 8.x
- Windows 或 Linux

### 4.2 编译说明

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

---

## 5. 数据库初始化

Windows：

```bat
start/windows/create_sql_table.bat
```

Linux：

```bash
sh start/linux/create_sql_table.sh
```

初始化 SQL 主要创建以下表：

- `external_system`
- `rpc_server_system`
- `account`
- `human_list`
- `human_info`
- `server_data`

说明：

- `human_info.role_data`：玩家模块化 JSON 存档
- `server_data`：服务级持久化，如全局聊天、好友、邮件等
- 执行脚本前，需修改脚本内的mysql地址、账号、密码

---

## 6. 服务器启动

### 6.1 配置文件

所有服务都从 `config/` 目录读取配置：

- `center-config.properties`
- `external-config.properties`
- `game-config.properties`
- `global-config.properties`
- `http-config.properties`
- `gmback-config.properties`

其中典型项有：

- MySQL 连接信息
- `master.id/master.address/master.port`
- `report.address`
- `http.port`
- `admin.port`
- `admin.user/admin.password`
- `admin.uipath`
- `config.path`

根据自己的环境，修改为正确配置

### 6.2 Windows 一键启动

```bat
start/windows/server_run_all.bat
```

### 6.3 Linux 一键启动

```bash
sh start/linux/server_run_all.sh
```

Linux 脚本使用 `pm2` 管理各个进程。需安装 PM2，并根据环境配置，开放端口。

```
npm install -g pm2
```
### 6.4 docker启动

详见文档：docs/docker启动流程.md

启动后会依次启动

1. center
2. external
3. global
4. game
5. http
6. gmback

### 6.5 GM后台登录

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

## 7. 客户端启动

### 7.1 发送消息工具

Windows：

```bat
start/windows/client.bat
```

本质入口：

- `sendmsg.main.ClientStartUp`

作用：

- 初始化 HTTP 地址获取器
- 注册协议解析器与处理器
- 打开消息发送工具

### 7.2 机器人客户端

Windows：

```bat
start/windows/bot.bat
```

本质入口：

- `bot.main.BotStartUp`

作用：

- 批量创建客户端
- 自动登录
- 定时发送 Ping
- 用于压测与稳定性测试

### 7.3 客户端配置

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

## 8. 已有业务模块一览

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

适合作为新增业务模块的参考模板。

---

## 9. 联系我

qq：1906438581

mail：lieng618@163.com
