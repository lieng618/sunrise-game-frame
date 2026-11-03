registerPage('quick-start', '快速开始', '环境搭建、编译、配置、启动服务', () => `
<h1>快速开始</h1>
<p class="page-desc">从零开始搭建 Sunrise Game Frame 开发环境并运行服务器</p>

<h2>环境要求</h2>
<table>
<thead><tr><th>依赖</th><th>版本要求</th><th>说明</th></tr></thead>
<tbody>
<tr><td>JDK</td><td>21+</td><td>推荐使用 Java 21 环境运行</td></tr>
<tr><td>Maven</td><td>3.8+</td><td>项目构建工具，聚合工程结构</td></tr>
<tr><td>MySQL</td><td>5.7+</td><td>数据持久化存储，需创建 sunrise 数据库（字符集 utf8mb4）</td></tr>
</tbody>
</table>

<h2>1. 编译项目</h2>
<p>在项目根目录执行一次聚合构建：</p>
<pre><code class="language-bash">mvn clean package
# 跳过测试：mvn clean package -DskipTests</code></pre>
<p>编译完成后，<code>start/jar/</code> 会生成以下产物：</p>
<table>
<thead><tr><th>JAR 文件</th><th>主类</th><th>说明</th><th>依赖配置</th></tr></thead>
<tbody>
<tr><td>sunrise-center.jar</td><td>CenterServerStartUp</td><td>中心服</td><td>center-config.properties</td></tr>
<tr><td>sunrise-external.jar</td><td>ExternalServerStartUp</td><td>对外网关服</td><td>external-config.properties</td></tr>
<tr><td>sunrise-game.jar</td><td>GameServerStartUp</td><td>游戏逻辑服</td><td>game-config.properties</td></tr>
<tr><td>sunrise-global.jar</td><td>GlobalServerStartUp</td><td>全局服</td><td>global-config.properties</td></tr>
<tr><td>sunrise-http.jar</td><td>HttpServerStartUp</td><td>HTTP 服务</td><td>http-config.properties</td></tr>
<tr><td>sunrise-gmback.jar</td><td>GmBackServerStartUp</td><td>GM 后台服务（前端见 gmback-ui）</td><td>gmback-config.properties</td></tr>
<tr><td>sunrise-runallone.jar</td><td>RunAllOneServerStartUp</td><td>单进程合服</td><td>runallone-config.properties</td></tr>
<tr><td>sunrise-client.jar</td><td>ClientStartUp</td><td>消息发送客户端（Swing GUI）</td><td>client-config.properties</td></tr>
<tr><td>sunrise-bot.jar</td><td>BotStartUp</td><td>压测机器人（Swing GUI）</td><td>client-config.properties</td></tr>
<tr><td>sunrise-stress.jar</td><td>StressStartUp</td><td>压测统计（分阶段耗时 / 发包 TPS）</td><td>client-config.properties</td></tr>
<tr><td>sunrise-lib.jar</td><td>-</td><td>所有服务共享的运行时依赖与类</td><td>由启动 JAR 的 Class-Path 引用</td></tr>
<tr><td>hotswap-agent.jar</td><td>AgentBootstrap</td><td>热更 Agent 引导包</td><td>启动参数 <code>-javaagent</code></td></tr>
<tr><td>sunrise-hotfix.jar</td><td>-</td><td>game/network/gen/center 全量业务补丁</td><td>GM 代码热更</td></tr>
</tbody>
</table>
<div class="callout callout-warn">
    <p><strong>启动 JAR 不是单文件运行包。</strong>所有启动 JAR 都通过 Manifest 引用同目录的 <code>sunrise-lib.jar</code>。部署时必须一起复制，并使用仓库脚本中统一的 <code>-javaagent</code> 参数。</p>
</div>

<h2>2. 修改配置</h2>
<p>仓库只提交 <code>config/*-config.example.properties</code> 模板。首次使用可运行 <code>config/reset-config.bat</code> 或 <code>sh config/reset-config.sh</code> 生成实际配置，再按环境修改。相对路径以配置文件所在目录为基准，而不是进程启动目录。</p>
<pre><code class="language-properties"># ===== 数据库配置（所有服务共用）=====
jdbc.url=jdbc:mysql://127.0.0.1:3306/sunrise
jdbc.user=root
jdbc.password=your_password
jdbc.pool.maximum-size=5

# ===== 中心服地址（所有 RPC 服务共用）=====
master.id=1
master.address=127.0.0.1
master.port=8000

# ===== 本机上报 IP（各服务自己的 IP）=====
report.address=127.0.0.1

# ===== 对外服暴露 IP（客户端连接地址）=====
external.address=127.0.0.1

# ===== RPC 节点身份 =====
rpc.node.type=game
rpc.node.server-id=200
rpc.scan.packages=org.sunrise.game.game.service
rpc.call-enum-class=org.sunrise.game.genRpc.gen.CallEnum

# ===== 内部 RPC 握手认证（所有节点与中心服必须一致）=====
rpc.auth-token=\${RPC_AUTH_TOKEN:change-me}</code></pre>

<div class="callout callout-tip">
    <p><strong>💡 配置优先级</strong>：每个服务读取自己对应的 <code>xxx-config.properties</code>，但数据库和中心服地址是通用配置，每个配置文件都有。单进程模式使用 <code>runallone-config.properties</code>，其中 <code>master.id=0</code> 表示无需连接中心服。</p>
    <p>本地开发默认 <code>player.auth.enabled=false</code>，客户端可直接用 uid 登录；生产环境开启邮箱鉴权时需配置 <code>mail.smtp.*</code> 并保持 Http 与 Game 的 <code>player.jwt.*</code> 一致，详见 <a href="#/http-server">HTTP 服务</a> 与 <a href="#/config">配置参考</a>。</p>
</div>

<h2>3. 初始化数据库</h2>
<p>执行 SQL 脚本创建数据表，需先修改脚本内的 MySQL 连接信息。当前脚本会 <code>DROP DATABASE</code> 后重新创建 <code>sunrise</code>，不要对已有生产库直接执行。</p>
<div class="tabs">
    <button class="tab-btn active" onclick="switchTab(this, 'sql-win')">Windows</button>
    <button class="tab-btn" onclick="switchTab(this, 'sql-linux')">Linux</button>
</div>
<div class="tab-content active" id="sql-win">
<pre><code class="language-bash">cd start\\windows
.\\create_sql_table.bat</code></pre>
</div>
<div class="tab-content" id="sql-linux">
<pre><code class="language-bash">cd start/linux
sh create_sql_table.sh</code></pre>
</div>
<p>初始化会创建以下数据表：</p>
<table>
<thead><tr><th>表名</th><th>说明</th><th>关键字段</th></tr></thead>
<tbody>
<tr><td>external_system</td><td>对外服 ID、IP、状态与租约时间</td><td>id, ip, status, update_time</td></tr>
<tr><td>rpc_server_system</td><td>RPC 节点 ID、地址、状态与租约时间</td><td>id, ip, port, status, update_time</td></tr>
<tr><td>account</td><td>玩家账号表</td><td>id(accountId), uid, create_time, update_time</td></tr>
<tr><td>human_list</td><td>玩家角色摘要与 server_id 隔离</td><td>id, uid, human_id, server_id, pos, name, level</td></tr>
<tr><td>human_info</td><td>玩家完整模块存档</td><td>id, human_id(UNIQUE), role_data(MEDIUMBLOB)</td></tr>
<tr><td>server_data</td><td>RPC 服务与 GameSystem 存档</td><td>id, server_id, name, data(MEDIUMBLOB)</td></tr>
</tbody>
</table>

<h2>4. 启动服务器</h2>
<h3>方式一：多进程部署</h3>
<p>各服务独立运行；建议先启动中心服，再启动业务节点。业务节点会等待中心服广播连接信息，并周期性重连。</p>
<div class="tabs">
    <button class="tab-btn active" onclick="switchTab(this, 'run-win')">Windows</button>
    <button class="tab-btn" onclick="switchTab(this, 'run-linux')">Linux</button>
    <button class="tab-btn" onclick="switchTab(this, 'run-docker')">Docker</button>
</div>
<div class="tab-content active" id="run-win">
<pre><code class="language-bash"># 一键启动所有服务
start\\windows\\server_run_all.bat
# 注意：Windows 单服务脚本需要从 single 目录启动

# 或进入 single 目录手动逐个启动
cd start\\windows\\single
.\\center.bat
.\\external.bat
.\\game.bat
.\\global.bat
.\\http.bat
.\\gmback.bat</code></pre>
<p>多进程模式下启动中心服后，可打开 <code>http://127.0.0.1:8088/</code> 查看 RPC 节点与连接拓扑。</p>
</div>
<div class="tab-content" id="run-linux">
<pre><code class="language-bash"># 先安装 pm2 进程管理器
npm install -g pm2

# 一键启动所有服务
sh start/linux/server_run_all.sh</code></pre>
</div>
<div class="tab-content" id="run-docker">
<pre><code class="language-bash">cd start/docker
cp .env.example .env       # Windows: Copy-Item .env.example .env
# 修改 .env 中的密码、JWT、RPC token 与 EXTERNAL_ADDRESS
docker compose up -d --build</code></pre>
</div>

<h3>方式二：单进程部署</h3>
<p>所有内置业务服务合并到一个进程中运行，适合开发调试。使用 <code>RunAllOneServerStartUp</code>，<code>master.id=0</code> 表示不连接中心服；进程仍会访问数据库，并以 <code>rpc.node.server-id</code> 占用 RPC 节点记录。</p>
<pre><code class="language-bash"># Windows
cd start\\windows\\single
.\\runallone.bat

# Linux
sh start/linux/server_run_allone.sh</code></pre>

<h2>5. 启动客户端</h2>
<h3>Godot 游戏客户端</h3>
<p>独立仓库 <a href="https://gitee.com/lieng618/sunrise-game-frame-client" target="_blank" rel="noopener noreferrer">sunrise-game-frame-client</a>：Godot 4.6 + C# 实现的 2D 像素风 RPG 完整示例，覆盖登录、地图探索、多人同步、聊天/背包/邮件等玩法。默认连接 <code>http://127.0.0.1:8090</code> 网关，开发模式下输入 UID（如 <code>test</code>）即可联调。详见 <a href="#/client-example">客户端示例</a>。</p>
<pre><code class="language-bash">git clone https://gitee.com/lieng618/sunrise-game-frame-client.git
# 用 Godot 4.6 打开项目，F5 运行</code></pre>
<h3>消息发送工具</h3>
<p>基于 Swing 的 GUI 工具，支持多标签页，每标签页一个玩家连接。自动读取 proto 信息构建 UI，可向服务器发送消息包。仓库内 Java 工具仅发送 UID 登录请求，因此只适用于 <code>player.auth.enabled=false</code>。</p>
<pre><code class="language-bash">cd start\\windows
.\\client.bat</code></pre>
<h3>压测机器人</h3>
<p>支持批量创建客户端、一键登录、一键发消息包，统计连接/登录状态。</p>
<pre><code class="language-bash">cd start\\windows
.\\bot.bat</code></pre>
<h3>压测统计工具</h3>
<p>分阶段登录耗时、以服务器回包为准的发包 TPS，含客户端与服务端诊断。详见 <a href="#/stress-testing">压测统计</a>。</p>
<pre><code class="language-bash">cd start\\windows
.\\stress.bat</code></pre>

<h2>6. 访问 GM 后台</h2>
<p>GM 后台分为<strong>后端 API</strong>与<strong>前端页面</strong>两部分：</p>
<ul>
    <li><strong>gmback</strong>（<code>GmBackServer</code> 或合服 <code>RunAllOne</code>）：提供API服务，默认地址 <code>http://127.0.0.1:8010/api/...</code></li>
    <li><strong>gmback-ui</strong>：Vue 3 前端应用</li>
</ul>
<pre><code class="language-bash"># 确保已启动 gmback后台服务（多进程：gmback.bat；单进程：runallone.bat）

cd gmback-ui
npm install
npm run dev</code></pre>
<p>浏览器打开 <code>http://localhost:5173/</code>，使用配置中的账号登录（默认 <code>admin</code> / <code>sunrise</code>，见 <code>gmback-config.properties</code> 或 <code>runallone-config.properties</code> 的 <code>admin.user</code>、<code>admin.password</code>）。</p>
<p>登录后进入「节点监控」，应能看到对外服、游戏服等 RPC 节点；客户端能正常登录则说明服务运行成功。生产环境需 <code>npm run build</code> 后用 Nginx 托管 <code>dist/</code> 并反代 <code>/api</code>，详见 <a href="#/gmback-server">GM 后台文档</a>。</p>

`);
