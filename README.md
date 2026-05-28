<div class="hero">
    <h1>Sunrise Game Frame</h1>
    <p class="subtitle">基于 Java 21 实现的轻量级分布式游戏服务器框架，采用"中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台 + 可扩展RPC服务"的多服务多进程架构，也可部署为多服务单进程，不同服务支持随意的拆分和组合，支持全球通服模式和滚服模式（一套业务代码，仅修改部署方式），框架不依赖任何第三方中间件，仅需Java环境即可运行，代码简洁规范、易理解、易扩展。</p>
    <div class="hero-badges">
        <span class="hero-badge">☕ Java 21</span>
        <span class="hero-badge">🌐 Netty</span>
        <span class="hero-badge">📦 Protobuf</span>
        <span class="hero-badge">🔗 RPC</span>
        <span class="hero-badge">🗄️ MySQL</span>
        <span class="hero-badge">🐳 Docker</span>
    </div>
</div>

<h2>项目文档</h2>
<a href="https://sunrise-game-frame.pages.dev">https://sunrise-game-frame.pages.dev</a>

<h2>核心特性</h2>
<div class="feature-grid">
    <div class="feature-item"><span class="feature-icon">🏗️</span><span class="feature-text">分布式多节点架构，不同服务可随意组合拆分为多进程或单进程部署，支持动态扩容</span></div>
    <div class="feature-item"><span class="feature-icon">📡</span><span class="feature-text">RPC 框架，支持随机/广播/定向三种调用模式，含回调与超时机制；中心服可配置连接策略，内置拓扑可视化面板</span></div>
    <div class="feature-item"><span class="feature-icon">📭</span><span class="feature-text">零中间件依赖，无需 Redis / ZooKeeper / MQ，真正做到轻量级</span></div>
    <div class="feature-item"><span class="feature-icon">🚀</span><span class="feature-text">业务代码无需修改，仅修改部署方式，即可支持全球通服与滚服模式</span></div>
    <div class="feature-item"><span class="feature-icon">🔒</span><span class="feature-text">RPC服与游戏服单线程处理业务，无需加锁，无需考虑线程安全问题</span></div>
    <div class="feature-item"><span class="feature-icon">🔌</span><span class="feature-text">对外服同时支持TCP / WebSocket / KCP 三种协议</span></div>
    <div class="feature-item"><span class="feature-icon">🛠️</span><span class="feature-text">完善的GM 后台管理，实现节点监控、策划表格与代码热更、运营服务、权限管理等</span></div>
    <div class="feature-item"><span class="feature-icon">🤖</span><span class="feature-text">消息发送工具、压测机器人与压测统计工具，便于联调与性能基准</span></div>
    <div class="feature-item"><span class="feature-icon">🔃</span><span class="feature-text">业务模块化，已实现游戏必需的模块如登录、存库、消息处理、玩家模块、系统模块等</span></div>
</div>

<h2>快速开始</h2>
<p>环境要求：<strong>JDK 21+</strong>、<strong>Maven 3.8+</strong>、<strong>MySQL 5.7+</strong></p>

<h4>1. 编译</h4>
<pre><code class="language-bash">git clone https://gitee.com/lieng618/sunrise-game-frame.git
或 git clone https://github.com/lieng618/sunrise-game-frame.git
cd sunrise-game-frame
mvn install
mvn clean package</code></pre>
<p>编译完成后，会在<code>start/jar/</code> 目录下生成可执行 jar包</p>

<h4>2. 初始化数据库</h4>
<p>执行建表脚本，需先修改脚本内的 MySQL 连接信息</p>
<pre><code class="language-bash"># Windows
start\windows\create_sql_table.bat</code></pre>
<pre><code class="language-bash"># Linux
sh start/linux/create_sql_table.sh</code></pre>

<h4>3. 单进程启动（RunAllOneServerStartUp）</h4>
<p>将对外服、游戏服、全局服、HTTP 服务、GM 后台合并到一个进程中运行，无需单独启动中心服，适合本地开发调试</p>
<p>修改配置文件： <code>config/runallone-config.properties</code>
<a href="https://sunrise-game-frame.pages.dev/#/config">配置参考</a></p>
<pre><code class="language-bash"># Windows
start\windows\single\runallone.bat</code></pre>
<pre><code class="language-bash"># Linux
sh start/linux/server_run_allone.sh</code></pre>

<h4>4.访问 GM 后台</h4>
<pre><code class="language-bash">cd gmback-ui
npm install
npm run dev</code></pre>

<p>浏览器访问 <a href="http://localhost:5173">http://localhost:5173</a>，使用配置中的账号登录（默认 admin / sunrise，见runallone-config.properties）</p>

<h4>5. 客户端启动</h4>
<p>消息发送工具：支持多标签页，每标签页一个玩家连接，可向服务器发送消息包</p>
<pre><code class="language-bash">start/windows/client.bat</code></pre>
<p>压测机器人工具：支持批量创建客户端、一键登录、批量定时向服务器发消息包</p>
<pre><code class="language-bash">start/windows/bot.bat</code></pre>
<p>压测统计工具：登录耗时 + 以服务器回包为准的发包 TPS，含端到端诊断（详见 <a href="https://sunrise-game-frame.pages.dev/#/stress-testing">压测统计文档</a>）</p>
<pre><code class="language-bash">start/windows/stress.bat</code></pre>

<h4>6. 生产环境部署</h4>
<p>生产环境建议多服务多进程运行，根据需求拆分与合并服务。详见文档：<a href="https://sunrise-game-frame.pages.dev/#/deployment">部署指南</a></p>

<h2>架构图</h2>
<div class="arch-diagram">
    <img src="https://files.seeusercontent.com/2026/04/27/Im3e/sunrise-game-frame.png" alt="架构图" style="max-width:100%;border-radius:8px;" />
</div>

<h2>各服务职责</h2>
<div class="card-grid">
    <div class="card">
        <h4>CenterServer</h4>
        <div class="card-desc">中心服，所有 RPC 节点的注册中心，负责节点发现与信息广播，不执行任何业务 RPC，只做"节点通讯录同步"。各节点上报时携带 nodeType，中心服按 <code>rpc.connect.*</code> 策略决定向谁广播地址（未配置则全量互连）。内置 Dashboard（默认 <code>http://127.0.0.1:8088</code>）实时展示在线节点与 RPC 连接拓扑。中心服挂掉不影响已互连的节点间通信，但新节点无法加入；支持断线重连。</div>
    </div>
    <div class="card">
    <h4>ExternalServer</h4>
        <div class="card-desc">对外网关服，同时支持 TCP、WebSocket、KCP 三种协议，是客户端与服务器之间的唯一入口，验证客户端首包认证、分配 connectionId、做消息频率限制、转发消息到 Game 服、接收 Game 回包发回客户端。</div>
    </div>
    <div class="card">
    <h4>GameServer</h4>
        <div class="card-desc">游戏核心业务服，处理登录流程（创建/加载账号、角色）、玩家对象管理（HumanObject）、模块生命周期（init/load/save/sendToClient）、协议路由（@MsgHandlerClass、@MsgHandlerMethod）、定时数据库存档、调用 Global 跨服服务，处理gm后台指令。</div>
    </div>
    <div class="card">
    <h4>GlobalServer</h4>
        <div class="card-desc">全局跨服服务，所有需要跨服共享的数据和逻辑都放在这里，目前实现了：聊天服务（GlobalChatService）、好友服务（GlobalFriendService）、邮件服务（GlobalMailService）、玩家简要信息查询（GlobalPlayerInfoService）。</div>
    </div>
    <div class="card">
    <h4>HttpServer</h4>
        <div class="card-desc">HTTP 服务，客户端登录时，先通过curl请求分配对外服地址。提供对外服地址、服务器状态和白名单接口。</div>
    </div>
    <div class="card">
    <h4>GmBackServer</h4>
        <div class="card-desc">GM 后台 API服务，提供 REST API 并与 Game 服同步 GM 指令。前后端分离，浏览器页面由前端工程 gmback-ui独立部署。提供运营指令（发邮件、踢人、封禁/禁言、在线玩家、服务器开关、白名单、公告、兑换码）、节点监控、配置与代码热更、用户权限与操作日志。</div>
    </div>
    <div class="card">
        <h4>可扩展的 RPC 服务</h4>
        <div class="card-desc">除内置进程外，可按同一套模式新增任意 RPC 业务进程：创建 RpcNode、接入中心服、注册 @RpcService，并与集群内其他节点互连通信。详见文档：<a href="https://sunrise-game-frame.pages.dev/#/custom-rpc-service">可扩展 RPC 服务</a>。</div>
    </div>
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
│       ├─ rpc/                # RPC 框架（注解/节点/调用/服务管理/policy 连接策略）
│       ├─ db/                 # DbService（HikariCP）/ 实体类
│       ├─ config/             # ConfigReader 配置加载
│       └─ utils/              # IdGenerator / LogCore / JwtUtil / Utils
├─ gen/                        # 协议 / RPC 枚举 / DB 实体 生成代码
│   └─ src/main/java/org/sunrise/game/
│       ├─ genProto/           # .proto 文件 + 生成的 Java 协议类
│       ├─ genRpc/             # GenRpcStartUp → CallEnum.java
│       └─ genDb/              # GenDbStartUp → 实体类
├─ game/                       # 游戏主逻辑（多服务实现）
│   └─ src/main/java/org/sunrise/game/
│       ├─ external/           # 对外服（TCP/WS/KCP 网关）
│       ├─ game/               # 游戏服（玩家对象/模块/协议路由/系统）
│       ├─ global/             # 全局服（聊天/好友/邮件/玩家信息）
│       ├─ http/               # HTTP 服务（地址分发）
│       ├─ gmback/             # GM 后台（REST API）
│       └─ runone/             # 单进程部署（将所有模块集成到一个进程中运行）
├─ gmback-ui/                  # GM 后台前端（Vite + Vue 3 SPA）
├─ hotswap/                    # java代码热更新支持
├─ client/                     # 客户端工具
│   └─ src/main/java/
│       ├─ core/               # SocketClient / TcpClient / WsClient / KcpClient
│       ├─ swing/              # MainFrame / SendMsgFrame（消息发送工具）
│       ├─ bot/                # BotManager / BotFrame（压测机器人工具）
│       └─ stress/             # StressManager / StressFrame（压测统计工具）
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
<tr><td>Netty</td><td>4.2.12.Final</td><td>高性能网络框架</td><td>network</td></tr>
<tr><td>java-Kcp</td><td>1.6</td><td>KCP 传输协议支持</td><td>network</td></tr>
<tr><td>Protobuf</td><td>4.28.2</td><td>客户端业务消息序列化</td><td>gen</td></tr>
<tr><td>MessagePack</td><td>0.9.11</td><td>RPC 消息序列化</td><td>network</td></tr>
<tr><td>FastJSON</td><td>2.0.32</td><td>JSON 序列化</td><td>network/game</td></tr>
<tr><td>MySQL</td><td>5.7+</td><td>数据持久化</td><td>network</td></tr>
<tr><td>HikariCP</td><td>5.0.1</td><td>数据库连接池</td><td>network</td></tr>
<tr><td>Javalin</td><td>6.7.0</td><td>轻量级 Web 框架</td><td>game</td></tr>
<tr><td>Luban</td><td>4.x</td><td>游戏配置工作流（Excel → JSON）</td><td>tables</td></tr>
<tr><td>yitter</td><td>1.0.6</td><td>分布式雪花算法 ID 生成</td><td>network</td></tr>
<tr><td>JWT</td><td>0.13.0</td><td>GM 后台鉴权</td><td>network</td></tr>
<tr><td>Vue</td><td>3.5+</td><td>GM后台前端 SPA</td><td>gmback-ui</td></tr>
<tr><td>Vite</td><td>6.x</td><td>GM后台前端 构建与开发</td><td>gmback-ui</td></tr>
</tbody>
</table>

<table>
<thead><tr><th>模块</th><th>artifactId</th><th>职责</th><th>关键包 / 说明</th></tr></thead>
<tbody>
<tr><td>network</td><td>network</td><td>核心网络/RPC/DB 基础设施</td><td>org.sunrise.game.network</td></tr>
<tr><td>center</td><td>center</td><td>中心服（节点发现与注册）</td><td>org.sunrise.game.center</td></tr>
<tr><td>game</td><td>game</td><td>业务逻辑（支持多进程或单进程部署）</td><td>org.sunrise.game.game/external/global/gmback/http</td></tr>
<tr><td>gmback-ui</td><td>-</td><td>GM 后台前端</td><td>/gmback-ui</td></tr>
<tr><td>gen</td><td>gen</td><td>代码生成（RPC 枚举、DB 实体、Proto）</td><td>org.sunrise.game.gen*</td></tr>
<tr><td>client</td><td>client</td><td>客户端工具（消息发送器、压测机器人、压测统计）</td><td>org.sunrise.game.client</td></tr>
</tbody>
</table>

<h2>测试机压测记录</h2>
<p> Windows 10，Intel i7-8700，6 核 12 线程 @ 3.20GHz，内存 64 GB</p>
<p>启动 RunAllOne 单进程、客户端连接方式为 TCP。启动服务器后不再关闭，一次运行内连续压测各档位（压测多次求平均）。</p>
<p>以下测试为客户端进行了完整的登录流程，登录成功后再进行发包。由对外服务接收到消息，转发到游戏服处理。消息包结构为Ping包（<code>C2S_ClientPing</code> / <code>S2C_ClientPing</code>）</p>

<table>
<thead>
<tr>
    <th>在线人数</th>
    <th>发包总数</th>
    <th>发送耗时(ms)</th>
    <th>总耗时(ms)</th>
    <th>回包窗口(ms)</th>
    <th>TPS(全程)</th>
    <th>TPS(回包窗口)</th>
</tr>
</thead>
<tbody>
<tr><td>100</td><td>10000</td><td>36</td><td>49</td><td>36</td><td>204081.63</td><td>277777.78</td></tr>
<tr><td>100</td><td>50000</td><td>219</td><td>230</td><td>218</td><td>217391.30</td><td>229357.80</td></tr>
<tr><td>100</td><td>100000</td><td>410</td><td>423</td><td>407</td><td>236406.62</td><td>245700.25</td></tr>
<tr><td>300</td><td>100000</td><td>345</td><td>354</td><td>325</td><td>282485.88</td><td>307692.31</td></tr>
<tr><td>500</td><td>300000</td><td>996</td><td>1010</td><td>970</td><td>297029.70</td><td>309278.35</td></tr>
<tr><td>1000</td><td>500000</td><td>1668</td><td>1706</td><td>1625</td><td>293083.24</td><td>307692.31</td></tr>
<tr><td>2000</td><td>1000000</td><td>3352</td><td>3385</td><td>3291</td><td>295420.97</td><td>303859.01</td></tr>
<tr><td>2000</td><td>1500000</td><td>4852</td><td>4883</td><td>4733</td><td>307188.20</td><td>316923.73</td></tr>
<tr><td>2000</td><td>2000000</td><td>6498</td><td>6512</td><td>6380</td><td>307125.31</td><td>313479.62</td></tr>
</tbody>
</table>