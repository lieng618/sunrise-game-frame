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
<p>在项目根目录执行 Maven 编译：</p>
<pre><code class="language-bash">mvn install
mvn clean package</code></pre>
<p>编译完成后，会在 <code>start/jar/</code> 目录生成以下可执行 JAR：</p>
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
</tbody>
</table>

<h2>2. 修改配置</h2>
<p>所有配置文件位于 <code>config/</code> 目录，根据你的环境修改以下关键配置：</p>
<pre><code class="language-properties"># ===== 数据库配置（所有服务共用）=====
jdbc.url=jdbc:mysql://127.0.0.1:3306/sunrise?useUnicode=true&characterEncoding=utf8mb4
jdbc.user=root
jdbc.password=your_password
jdbc.maximumPoolSize=5

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
rpc.node.serverId=200</code></pre>

<div class="callout callout-tip">
    <p><strong>💡 配置优先级</strong>：每个服务读取自己对应的 <code>xxx-config.properties</code>，但数据库和中心服地址是通用配置，每个配置文件都有。单进程模式使用 <code>runallone-config.properties</code>，其中 <code>master.id=0</code> 表示无需连接中心服。</p>
</div>

<h2>3. 初始化数据库</h2>
<p>执行 SQL 脚本创建数据表，需先修改脚本内的 MySQL 连接信息：</p>
<div class="tabs">
    <button class="tab-btn active" onclick="switchTab(this, 'sql-win')">Windows</button>
    <button class="tab-btn" onclick="switchTab(this, 'sql-linux')">Linux</button>
</div>
<div class="tab-content active" id="sql-win">
<pre><code class="language-bash">start\\windows\\create_sql_table.bat</code></pre>
</div>
<div class="tab-content" id="sql-linux">
<pre><code class="language-bash">sh start/linux/create_sql_table.sh</code></pre>
</div>
<p>初始化会创建以下数据表：</p>
<table>
<thead><tr><th>表名</th><th>说明</th><th>关键字段</th></tr></thead>
<tbody>
<tr><td>external_system</td><td>对外服地址管理，自动分配端口</td><td>id, ip, port, status</td></tr>
<tr><td>rpc_server_system</td><td>RPC 节点地址管理，自动分配端口</td><td>id, ip, port, status</td></tr>
<tr><td>account</td><td>玩家账号表</td><td>id(accountId), uid</td></tr>
<tr><td>human_list</td><td>玩家角色列表</td><td>id, uid, human_id, server_id, pos, name, level</td></tr>
<tr><td>human_info</td><td>玩家信息存档表</td><td>id, human_id(UNIQUE), role_data(MEDIUMBLOB)</td></tr>
<tr><td>server_data</td><td>服务信息存档表</td><td>id, server_id, name, data(MEDIUMBLOB)</td></tr>
</tbody>
</table>

<h2>4. 启动服务器</h2>
<h3>方式一：多进程部署</h3>
<p>各服务独立运行，支持动态扩容。启动顺序：所有启动类无严格顺序，但最好是先启动Center</p>
<div class="tabs">
    <button class="tab-btn active" onclick="switchTab(this, 'run-win')">Windows</button>
    <button class="tab-btn" onclick="switchTab(this, 'run-linux')">Linux</button>
    <button class="tab-btn" onclick="switchTab(this, 'run-docker')">Docker</button>
</div>
<div class="tab-content active" id="run-win">
<pre><code class="language-bash"># 一键启动所有服务
start\\windows\\server_run_all.bat

# 或手动逐个启动（推荐调试时使用）
start\\windows\\center.bat      # 1. 先启动中心服
start\\windows\\external.bat    # 2. 启动对外服
start\\windows\\game.bat        # 3. 启动游戏服
start\\windows\\global.bat      # 4. 启动全局服
start\\windows\\http.bat        # 5. 启动 HTTP 服务
start\\windows\\gmback.bat      # 6. 启动 GM 后台</code></pre>
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
docker compose up -d --build</code></pre>
</div>

<h3>方式二：单进程部署</h3>
<p>所有服务合并到一个进程中运行，适合开发调试。使用 <code>RunAllOneServerStartUp</code>，<code>master.id=0</code> 跳过中心服连接。</p>
<pre><code class="language-bash"># Windows
start\\windows\\single\\runallone.bat

# Linux
sh start/linux/server_run_allone.sh</code></pre>

<h2>5. 启动客户端</h2>
<h3>消息发送工具</h3>
<p>基于 Swing 的 GUI 工具，支持多标签页，每标签页一个玩家连接。自动读取 proto 信息构建 UI，可向服务器发送消息包。</p>
<pre><code class="language-bash">start\\windows\\client.bat</code></pre>
<h3>压测机器人</h3>
<p>支持批量创建客户端、一键登录、一键发消息包，统计连接/登录状态。</p>
<pre><code class="language-bash">start\\windows\\bot.bat</code></pre>
<h3>压测统计工具</h3>
<p>分阶段登录耗时、以服务器回包为准的发包 TPS，含客户端与服务端诊断。详见 <a href="#/stress-testing">压测统计</a>。</p>
<pre><code class="language-bash">start\\windows\\stress.bat</code></pre>

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
