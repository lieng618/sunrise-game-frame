registerPage('home', '项目概览', 'Sunrise Game Frame 分布式游戏服务器框架介绍', () => `
<div class="hero">
    <img class="hero-logo" src="img/logo_big.png" alt="Sunrise Game Frame" width="1536" height="1024">
    <p class="subtitle">基于 Java 21 实现的轻量级分布式游戏服务器框架，采用"中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台 + 可扩展 RPC 服务"的多进程架构，也可组合为单进程运行。框架不依赖 Redis、ZooKeeper 或 MQ，仅使用 JDK、Maven 和 MySQL；支持无区服组合部署，也支持按 server_id 隔离角色的区服模式。</p>
    <div class="hero-badges">
        <span class="hero-badge">☕ Java 21</span>
        <span class="hero-badge">🌐 Netty</span>
        <span class="hero-badge">📦 Protobuf</span>
        <span class="hero-badge">🔗 RPC</span>
        <span class="hero-badge">🗄️ MySQL</span>
        <span class="hero-badge">🐳 Docker</span>
    </div>
    <div class="hero-actions">
        <a href="#/quick-start" class="hero-btn">开始快速上手</a>
        <a href="#/architecture" class="hero-btn">查看架构总览</a>
    </div>
</div>

<h2>核心特性</h2>
<div class="feature-grid">
    <div class="feature-item"><span class="feature-icon">🏗️</span><span class="feature-text">分布式多节点架构，可组合为多进程或单进程；RPC 节点可动态发现，有状态服务需单写者或分片</span></div>
    <div class="feature-item"><span class="feature-icon">📡</span><span class="feature-text">RPC 支持随机/广播/定向调用、参数类型转换、回调、超时与内部握手认证；中心服可配置连接策略</span></div>
    <div class="feature-item"><span class="feature-icon">📭</span><span class="feature-text">无需 Redis / ZooKeeper / MQ，仅需 JDK、Maven 与 MySQL</span></div>
    <div class="feature-item"><span class="feature-icon">🚀</span><span class="feature-text">通过 server_id 与部署组合支持通服和分区模式；有状态 Global 服务需单写者或分片</span></div>
    <div class="feature-item"><span class="feature-icon">🔒</span><span class="feature-text">每个 RPC 节点的业务消息由 DispatchThread 串行消费；网络 I/O、HTTP 与 DB 执行器独立运行</span></div>
    <div class="feature-item"><span class="feature-icon">🔌</span><span class="feature-text">对外服同时支持TCP / WebSocket / KCP 三种协议</span></div>
    <div class="feature-item"><span class="feature-icon">🛠️</span><span class="feature-text">GM 后台（gmback API + gmback-ui SPA）：节点监控、配置/代码热更、运营工具等</span></div>
    <div class="feature-item"><span class="feature-icon">🤖</span><span class="feature-text">消息发送工具、压测机器人与压测统计工具，便于联调与性能基准</span></div>
    <div class="feature-item"><span class="feature-icon">🔃</span><span class="feature-text">业务模块化，已实现游戏必需的模块如登录、存库、消息处理、玩家模块、系统模块等</span></div>
</div>

<h2>项目结构</h2>
<pre><code class="language-bash">sunrise-game-frame/
├─ pom.xml                     # Maven 聚合工程（父 POM）
├─ config/                     # 配置模板、ENV.md 与重置脚本
│   └─ *-config.example.properties
├─ center/                     # 中心服启动模块
├─ network/                    # 网络层、DB、RPC、基础工具（所有服务依赖）
│   └─ src/main/java/org/sunrise/game/
│       ├─ core/               # BaseServer / BaseClient / 消息 / 编解码
│       ├─ rpc/                # RPC 框架（注解/节点/调用/服务管理/policy 连接策略）
│       ├─ db/                 # DbService（HikariCP）/ 实体类
│       ├─ config/             # ConfigReader 与 \${ENV:default}
│       ├─ graceful/           # 有序优雅停机
│       ├─ jwt/                # JwtUtil / MailUtil / PasswordUtil
│       └─ utils/              # IdGenerator / NodeTimeouts / Utils
├─ gen/                        # 协议 / RPC 枚举 / DB 实体 生成代码
│   └─ src/main/java/org/sunrise/game/
│       ├─ genProto/           # .proto 文件 + 生成的 Java 协议类
│       ├─ genRpc/             # GenRpcStartUp → CallEnum.java
│       └─ genDb/              # GenDbStartUp → 实体类
├─ game/                       # 游戏主逻辑（多服务实现）
│   └─ src/main/java/org/sunrise/game/
│       ├─ external/           # 对外服（TCP/WS/KCP 网关）
│       ├─ game/               # 游戏服（玩家对象/模块/协议路由/系统）
│       ├─ global/             # 全局服（聊天/好友/邮件/玩家信息/排行榜）
│       ├─ http/               # HTTP 服务（地址分发 + 邮箱注册登录）
│       ├─ gmback/             # GM 后台（REST API）
│       └─ runone/             # 单进程部署（将所有模块集成到一个进程中运行）
├─ gmback-ui/                  # GM 后台前端（Vite + Vue 3 SPA）
├─ hotswap/                    # Java Agent 与类重定义内核
├─ hotfix/                     # 全量业务热更补丁模块
├─ client/                     # 客户端工具
│   └─ src/main/java/
│       ├─ core/               # SocketClient / TcpClient / WsClient / KcpClient
│       ├─ sendmsg/            # MainFrame / SendMsgFrame（消息发送工具）
│       ├─ bot/                # BotManager / BotFrame（压测机器人）
│       └─ stress/             # StressManager / StressFrame（压测统计与 TPS）
├─ start/                      # 启动脚本
│   ├─ windows/                # .bat 脚本（多进程/单进程/客户端/机器人）
│   ├─ linux/                  # .sh 脚本（pm2 管理）
│   └─ docker/                 # Docker Compose + 容器化配置
├─ tables/                     # Excel 配置表 + Luban 生成配置
│   ├─ Datas/                  # .xlsx 配置源文件
│   └─ json/                   # Luban 生成的 JSON 配置
└─ docs/                       # 架构文档 </code></pre>

<h2>技术栈</h2>
<table>
<thead><tr><th>技术</th><th>版本</th><th>用途</th><th>所在模块</th></tr></thead>
<tbody>
<tr><td>Java</td><td>21</td><td>运行时必备环境</td><td>全局</td></tr>
<tr><td>Maven</td><td>3.8+</td><td>聚合工程构建</td><td>根 pom.xml</td></tr>
<tr><td>slf4j / Logback</td><td>2.0.13 / 1.5.28</td><td>日志门面与实现</td><td>network</td></tr>
<tr><td>Netty</td><td>4.2.12.Final</td><td>高性能网络框架</td><td>network</td></tr>
<tr><td>java-Kcp</td><td>1.6</td><td>KCP 传输协议支持</td><td>network</td></tr>
<tr><td>Protobuf</td><td>4.28.2</td><td>客户端业务消息序列化</td><td>gen</td></tr>
<tr><td>MessagePack</td><td>0.9.11</td><td>RPC 消息序列化</td><td>network</td></tr>
<tr><td>FastJSON</td><td>2.0.32</td><td>JSON 序列化</td><td>network/game</td></tr>
<tr><td>MySQL / Connector-J</td><td>5.7+ / 9.6.0</td><td>数据持久化与 JDBC 驱动</td><td>network</td></tr>
<tr><td>HikariCP</td><td>5.0.1</td><td>数据库连接池</td><td>network</td></tr>
<tr><td>Javalin</td><td>6.7.0</td><td>轻量级 Web 框架</td><td>game</td></tr>
<tr><td>Luban</td><td>4.x</td><td>游戏配置工作流（Excel → JSON）</td><td>tables</td></tr>
<tr><td>yitter</td><td>1.0.6</td><td>分布式雪花算法 ID 生成</td><td>network</td></tr>
<tr><td>JWT</td><td>0.13.0</td><td>GM 后台与玩家登录鉴权</td><td>network</td></tr>
<tr><td>Jakarta Mail</td><td>2.x</td><td>邮箱验证码发送</td><td>network</td></tr>
<tr><td>Vue</td><td>3.5+</td><td>GM后台前端 SPA</td><td>gmback-ui</td></tr>
<tr><td>Vite</td><td>6.x</td><td>GM后台前端 构建与开发</td><td>gmback-ui</td></tr>
</tbody>
</table>

<table>
<thead><tr><th>模块</th><th>artifactId</th><th>职责</th><th>关键包 / 说明</th></tr></thead>
<tbody>
<tr><td>network</td><td>network</td><td>核心网络/RPC/DB 基础设施</td><td>org.sunrise.game.core/rpc/db/config</td></tr>
<tr><td>center</td><td>center</td><td>中心服（节点发现、拓扑与热更入口）</td><td>org.sunrise.game.main/dashboard</td></tr>
<tr><td>game</td><td>game</td><td>业务逻辑（支持多进程或单进程部署）</td><td>org.sunrise.game.game/external/global/gmback/http</td></tr>
<tr><td>gmback-ui</td><td>-</td><td>GM 后台前端</td><td>/gmback-ui</td></tr>
<tr><td>gen</td><td>gen</td><td>代码生成（RPC 枚举、DB 实体、Proto）</td><td>org.sunrise.game.gen*</td></tr>
<tr><td>hotswap</td><td>hotswap</td><td>Agent 与类重定义</td><td>org.sunrise.game.core</td></tr>
<tr><td>hotfix</td><td>hotfix</td><td>全量业务热更补丁</td><td>sunrise-hotfix.jar</td></tr>
<tr><td>client</td><td>client</td><td>消息发送器、机器人、压测统计</td><td>core.* / sendmsg.* / bot.* / stress.*</td></tr>
</tbody>
</table>

<h2>快速导航</h2>
<div class="card-grid">
    <a href="#/quick-start" class="card">
        <div class="card-icon">⚡</div>
        <div class="card-title">快速开始</div>
        <div class="card-desc">环境搭建、编译、配置、启动服务的完整流程</div>
    </a>
    <a href="#/architecture" class="card">
        <div class="card-icon">🏛️</div>
        <div class="card-title">架构总览</div>
        <div class="card-desc">服务职责、核心数据流、业务线程归属与停机编排</div>
    </a>
    <a href="#/rpc" class="card">
        <div class="card-icon">🔗</div>
        <div class="card-title">RPC 框架</div>
        <div class="card-desc">节点发现、方法注册、三种调用模式、回调与超时</div>
    </a>
    <a href="#/development" class="card">
        <div class="card-icon">🛠️</div>
        <div class="card-title">开发指南</div>
        <div class="card-desc">新增业务模块的完整开发流程与常见坑</div>
    </a>
    <a href="#/protocol" class="card">
        <div class="card-icon">📋</div>
        <div class="card-title">协议规范</div>
        <div class="card-desc">Protobuf 协议定义、命名规则、各模块协议一览</div>
    </a>
    <a href="#/deployment" class="card">
        <div class="card-icon">🚀</div>
        <div class="card-title">部署指南</div>
        <div class="card-desc">本地部署、Docker 部署、Linux 部署、全球通服与滚服部署</div>
    </a>
</div>
`);
