<div class="hero">
    <h1>Sunrise Game Frame</h1>
    <p class="subtitle">基于 Java 21 + Maven 的分布式游戏服务器框架，采用"中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台"的多进程架构，也支持单进程部署，框架不依赖任何第三方中间件，安装Java + MySQL 环境即可运行</p>
    <div class="hero-badges">
        <span class="hero-badge">☕ Java 21</span>
        <span class="hero-badge">🌐 Netty</span>
        <span class="hero-badge">📦 Protobuf</span>
        <span class="hero-badge">🔗 自研 RPC</span>
        <span class="hero-badge">🗄️ MySQL</span>
        <span class="hero-badge">🐳 Docker</span>
    </div>
</div>

<h2>核心特性</h2>
<div class="feature-grid">
    <div class="feature-item"><span class="feature-icon">🏗️</span><span class="feature-text">分布式多节点架构，所有业务节点支持动态扩容</span></div>
    <div class="feature-item"><span class="feature-icon">📡</span><span class="feature-text">自研 RPC 框架，支持随机/广播/定向三种调用模式</span></div>
    <div class="feature-item"><span class="feature-icon">🚀</span><span class="feature-text">动态扩容支持，新节点上线自动注册发现</span></div>
    <div class="feature-item"><span class="feature-icon">📭</span><span class="feature-text">零中间件依赖，无需 Redis / ZooKeeper / MQ</span></div>
    <div class="feature-item"><span class="feature-icon">🔒</span><span class="feature-text">单线程 DispatchThread + 消息队列，无需加锁</span></div>
    <div class="feature-item"><span class="feature-icon">🔌</span><span class="feature-text">支持TCP / WebSocket / KCP 三协议统一接入</span></div>
    <div class="feature-item"><span class="feature-icon">🛠️</span><span class="feature-text">GM 后台管理，JWT 鉴权 + 节点监控 + 热更配置</span></div>
    <div class="feature-item"><span class="feature-icon">🤖</span><span class="feature-text">压测机器人，批量创建，登录 + 消息包发送，模拟真实环境</span></div>
    <div class="feature-item"><span class="feature-icon">🔃</span><span class="feature-text">业务代码编写规范，业务模块化设计，开发便捷</span></div>
</div>

<h2>项目文档</h2>

<a href="https://sunrise-game-frame.pages.dev">https://sunrise-game-frame.pages.dev</a>

<h2>架构图</h2>
<div class="arch-diagram">
    <img src="https://files.seeusercontent.com/2026/04/27/Im3e/sunrise-game-frame.png" alt="架构图" style="max-width:100%;border-radius:8px;" />
</div>

<h2>项目结构</h2>
<pre><code class="language-bash">sunrise-game-frame/
├─ pom.xml                     # Maven 聚合工程（父 POM）
├─ config/                     # 各服务 .properties 配置文件
│   ├─ center-config.properties
│   ├─ external-config.properties
│   ├─ game-config.properties
│   ├─ global-config.properties
│   ├─ http-config.properties
│   ├─ gmback-config.properties
│   ├─ client-config.properties
│   └─ runallone-config.properties
├─ center/                     # 中心服启动模块
├─ network/                    # 网络层、DB、RPC、基础工具（所有服务依赖）
│   └─ src/main/java/org/sunrise/game/
│       ├─ core/               # BaseServer / BaseClient / 消息 / 编解码
│       ├─ rpc/                # 自研 RPC 框架（注解/节点/调用/服务管理）
│       ├─ db/                 # DbService（HikariCP）/ 实体类
│       ├─ config/             # ConfigReader 配置加载
│       └─ utils/              # IdGenerator / LogCore / JwtUtil / Utils
├─ gen/                        # 协议 / RPC 枚举 / DB 实体 生成代码
│   └─ src/main/java/org/sunrise/game/
│       ├─ genProto/           # .proto 文件 + 生成的 Java 协议类
│       ├─ genRpc/             # GenRpcStartUp → CallEnum.java
│       └─ genDb/              # GenDbStartUp → Lombok @Data 实体类
├─ game/                       # 游戏主逻辑（多服务实现）
│   └─ src/main/java/org/sunrise/game/
│       ├─ external/           # 对外服（TCP/WS/KCP 网关）
│       ├─ game/               # 游戏服（玩家对象/模块/协议路由/系统）
│       ├─ global/             # 全局服（聊天/好友/邮件/玩家信息）
│       ├─ http/               # HTTP 服务（地址分发）
│       └─ gmback/             # GM 后台（REST API + 静态页面）
├─ client/                     # 客户端工具
│   └─ src/main/java/
│       ├─ core/               # SocketClient / TcpClient / WsClient / KcpClient
│       ├─ swing/              # MainFrame / SendMsgFrame（消息发送 GUI）
│       └─ bot/                # BotManager / BotFrame（压测机器人）
├─ admin-ui/                   # GM 后台前端（Vue 3 + Element Plus）
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
<tr><td>slf4j</td><td>2.0.13</td><td>日志输出与记录</td><td>全局</td></tr>
<tr><td>Netty</td><td>4.2.12.Final</td><td>高性能网络框架，TCP/WebSocket 通信</td><td>network</td></tr>
<tr><td>java-Kcp</td><td>1.6</td><td>KCP 传输协议支持（基于 Ukcp）</td><td>network</td></tr>
<tr><td>Protobuf</td><td>4.28.2</td><td>客户端业务消息序列化</td><td>gen</td></tr>
<tr><td>MessagePack</td><td>0.9.11</td><td>RPC 消息序列化（Call 对象）</td><td>network</td></tr>
<tr><td>FastJSON</td><td>2.0.32</td><td>JSON 序列化（玩家存档数据、Http 接口返回）</td><td>network/game</td></tr>
<tr><td>MySQL</td><td>5.7+</td><td>数据持久化（6 张核心sql表）</td><td>network</td></tr>
<tr><td>HikariCP</td><td>5.0.1</td><td>数据库连接池</td><td>network</td></tr>
<tr><td>Javalin</td><td>6.7.0</td><td>轻量级 Web 框架（HTTP 服务 + GM 后台）</td><td>game</td></tr>
<tr><td>Luban</td><td>4.x</td><td>游戏配置工作流（Excel → JSON），自定义模板支持配置热更新</td><td>tables</td></tr>
<tr><td>yitter</td><td>1.0.6</td><td>分布式雪花算法 ID 生成（分布式网络通信唯一消息ID生成）</td><td>network</td></tr>
<tr><td>JWT</td><td>0.13.0</td><td>GM 后台鉴权（HS256 签名 + Token 黑名单）</td><td>network</td></tr>
</tbody>
</table>

<h2>模块介绍</h2>
<table>
<thead><tr><th>模块</th><th>artifactId</th><th>职责</th><th>关键包</th></tr></thead>
<tbody>
<tr><td>network</td><td>network</td><td>核心网络/RPC/DB 基础设施</td><td>org.sunrise.game.network</td></tr>
<tr><td>center</td><td>center</td><td>中心服（节点发现与注册）</td><td>org.sunrise.game.center</td></tr>
<tr><td>game</td><td>game</td><td>业务逻辑（支持多进程或单进程部署）</td><td>org.sunrise.game.game/external/global/http/gmback</td></tr>
<tr><td>gen</td><td>gen</td><td>代码生成（RPC 枚举、DB 实体、Proto）</td><td>org.sunrise.game.gen*</td></tr>
<tr><td>client</td><td>client</td><td>客户端工具（消息发送器、压测机器人）</td><td>org.sunrise.game.client</td></tr>
</tbody>
</table>
