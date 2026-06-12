registerPage('deployment', '部署指南', '本地部署、Docker 部署、Linux 部署', () => `
<h1>部署指南</h1>
<p class="page-desc">支持本地部署、Docker 部署和 Linux 部署三种方式</p>

<h2>端口说明</h2>
<table>
<thead><tr><th>服务</th><th>端口</th><th>协议</th><th>用途</th></tr></thead>
<tbody>
<tr><td>CenterServer</td><td>8000</td><td>TCP</td><td>RPC 节点注册中心（内部）</td></tr>
<tr><td>Center Dashboard</td><td>8088</td><td>HTTP</td><td>RPC 拓扑可视化（0=关闭）</td></tr>
<tr><td>ExternalServer</td><td>10000</td><td>TCP</td><td>客户端 TCP 连接</td></tr>
<tr><td>ExternalServer</td><td>10001</td><td>WebSocket</td><td>客户端 WS 连接</td></tr>
<tr><td>ExternalServer</td><td>10002</td><td>KCP</td><td>客户端 KCP 连接</td></tr>
<tr><td>HttpServer</td><td>8090</td><td>HTTP</td><td>邮箱注册登录 + 地址分发接口</td></tr>
<tr><td>GmBackServer</td><td>8010</td><td>HTTP</td><td>GM 后台 REST API（gmback-ui 通过 /api 访问）</td></tr>
<tr><td>gmback-ui（前端目录）</td><td>5173</td><td>HTTP</td><td>开发环境下Vite 开发服务器，代理 /api → admin.port</td></tr>
<tr><td>MySQL</td><td>3306</td><td>TCP</td><td>数据库</td></tr>
<tr><td>RPC节点</td><td>20000 + </td><td>TCP</td><td>RPC 节点服务占用端口</td></tr>
</tbody>
</table>

<h2>本地部署</h2>
<h3>Windows 多进程</h3>
<pre><code class="language-bash"># 1. 编译
mvn clean package -DskipTests

# 2. 初始化数据库
start\\windows\\create_sql_table.bat

# 3. 一键启动所有服务
start\\windows\\server_run_all.bat

# 或手动逐个启动（推荐调试时使用）
start\\windows\\center.bat      # 1. 先启动中心服
start\\windows\\external.bat    # 2. 启动对外服
start\\windows\\game.bat        # 3. 启动游戏服
start\\windows\\global.bat      # 4. 启动全局服
start\\windows\\http.bat        # 5. 启动 HTTP 服务
start\\windows\\gmback.bat      # 6. 启动 GM 后台

# 或在编译器内运行启动
</code></pre>

<h3>Windows 单进程</h3>
<pre><code class="language-bash">start\\windows\\single\\runallone.bat</code></pre>

<h3>Linux 部署</h3>
<pre><code class="language-bash"># 1. 安装 pm2 进程管理器
npm install -g pm2

# 2. 编译
mvn clean package -DskipTests

# 3. 初始化数据库
sh start/linux/create_sql_table.sh

# 4. 多进程启动
sh start/linux/server_run_all.sh

# 5. 单进程启动
sh start/linux/server_run_allone.sh</code></pre>

<h2>Docker 部署</h2>
<h3>前提条件</h3>
<ul>
    <li>安装 Docker Desktop（Windows）或 Docker Engine（Linux）</li>
    <li>配置镜像加速器（国内用户）</li>
</ul>

<h3>部署步骤</h3>
<ol class="step-list">
    <li>编译项目：<code>mvn clean package -DskipTests</code></li>
    <li>修改 <code>start/docker/config/external-config.properties</code> 中的 external.address（必须改为客户端能访问到的 IP）</li>
    <li>构建并启动：<code>cd start/docker && docker compose up -d --build</code></li>
</ol>

<h3>Docker Compose 服务</h3>
<table>
<thead><tr><th>服务</th><th>端口映射</th><th>依赖</th><th>说明</th></tr></thead>
<tbody>
<tr><td>mysql</td><td>13306:3306</td><td>无</td><td>MySQL 数据库，健康检查 mysqladmin ping</td></tr>
<tr><td>center</td><td>8088:8088</td><td>mysql(healthy)</td><td>中心服 + RPC 拓扑 Dashboard</td></tr>
<tr><td>external</td><td>10000:10000, 10001:10001, 10002:10002</td><td>mysql(healthy), center</td><td>对外服</td></tr>
<tr><td>game</td><td>无</td><td>mysql(healthy), center</td><td>游戏服</td></tr>
<tr><td>global</td><td>无</td><td>mysql(healthy), center</td><td>全局服</td></tr>
<tr><td>http</td><td>8090:8090</td><td>mysql(healthy), center</td><td>HTTP 服务</td></tr>
<tr><td>gmback</td><td>8010:8010</td><td>mysql(healthy), center</td><td>GM 后台 API</td></tr>
</tbody>
</table>

<h3>启动顺序</h3>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-warning">MySQL</span>
        <span class="flow-arrow">→ 健康检查通过 →</span>
        <span class="flow-node flow-node-warning">Center</span>
        <span class="flow-arrow">→ 启动完成 →</span>
        <span class="flow-node flow-node-secondary">External/Game/Global/Http/GmBack</span>
    </div>
</div>

<h3>常用命令</h3>
<table>
<thead><tr><th>操作</th><th>命令</th></tr></thead>
<tbody>
<tr><td>首次部署</td><td><code>docker compose up -d --build</code></td></tr>
<tr><td>查看日志</td><td><code>docker compose logs -f center</code></td></tr>
<tr><td>查看所有服务状态</td><td><code>docker compose ps</code></td></tr>
<tr><td>停止服务</td><td><code>docker compose stop</code></td></tr>
<tr><td>重启服务</td><td><code>docker compose restart</code></td></tr>
<tr><td>删除容器</td><td><code>docker compose down</code></td></tr>
<tr><td>彻底清理（含数据卷）</td><td><code>docker compose down -v</code></td></tr>
<tr><td>重建单个服务</td><td><code>docker compose up -d --build game</code></td></tr>
</tbody>
</table>

<h3>常见问题</h3>
<div class="callout callout-warn">
    <p><strong>MySQL 端口冲突</strong>：修改 docker-compose.yml 中的宿主机端口，如 <code>"13306:3306"</code></p>
</div>
<div class="callout callout-warn">
    <p><strong>客户端连接不上</strong>：确保 external.address 是客户端能访问到的 IP，不能是容器名或 127.0.0.1（Docker 环境下应使用宿主机 IP）</p>
</div>
<div class="callout callout-info">
    <p><strong>使用宿主机 MySQL</strong>：删除 docker-compose.yml 中的 mysql 服务，配置 jdbc.url 为 <code>jdbc:mysql://host.docker.internal:3306/sunrise</code></p>
</div>
<div class="callout callout-info">
    <p><strong>Docker Hub 连接超时</strong>：配置镜像加速器，或使用国内镜像源</p>
</div>

<h2>部署方式扩展</h2>
以上展示的是，不同服务节点，分别使用一个进程部署（多进程）或者都在同一个进程部署（单进程）。那么在实际生产中，该如何部署呢？
首先中心服一定只有一个，让所有服务节点都连接同一中心服，实现rpc通信。
HTTP服一般也只部署一个，因为有白名单、地址分配、kcp连接id分配的业务，多个节点还需考虑数据同步的问题，客户端只请求一个地址获取相关数据是比较便捷的。
GM 后台一般也只部署一套：<strong>gmback</strong>（API 进程）+ <strong>gmback-ui</strong>（Nginx 托管前端，反代 <code>/api</code>），在同一域名即可管理所有节点与玩家（封禁、发邮件等）。
Global服拆分时，一般一个业务作为一个节点，也可以多个业务同一节点（如果数据量不大）。如果想一个业务拆分多个节点，需在业务层面实现分节点处理
（比如将邮件服务拆分为多个节点，根据玩家id计算落到哪个节点，每个节点管理一部分玩家的邮件信息）。
<h3>全球通服</h3>
如果想做一个全球通服的游戏，不存在区服之分，比如一些竞技游戏如CS、LOL。所有服务节点都会连接同一中心服，客户端会通过http服务随机分配对外服地址，对外服收到客户端的登录请求及后续消息，
发起rpc调用时，会随机选择一个游戏服节点，所有游戏服读取的是同一个数据库。登录协议C2S_SelectHuman中有服务器id字段，由客户端登录选取角色时设置，通服模式下，这个值是无意义的，服务器id可以直接设置为0，不论玩家在哪个游戏服，读取的是同一表，查询出的数据永远都是同一份。
对于系统级的数据，GameSystemUtils中的数据加载与存储，目前系统仅有ResetSystem（计算跨天与跨周）做了存库操作，所有游戏服计算的时间是一样的，因此不同的游戏服都存自己的系统数据也没问题，业务设计时需考虑数据是否应该只存一份的问题，一般来说系统只是辅助做一些全局数据内存的操作，比如登录排队系统、地图系统，都是无需存库的系统。
需要存库的系统，还是建议继承BaseService实现。ServiceManager中的数据加载与存储要考虑数据同步问题，比如排行榜一定是所有游戏服都读同一份数据，因此可以使用一个服务节点存储排行榜。
<table>
<thead><tr><th>节点类型</th><th>部署方式</th></tr></thead>
<tbody>
<tr><td>中心服</td><td>部署一个中心服，一个进程</td></tr>
<tr><td>HTTP服</td><td>部署一个HTTP服，一个进程</td></tr>
<tr><td>GM后台</td><td>部署一个GM后台服 + gmback-ui（前端）</td></tr>
<tr><td>对外服</td><td>部署多个对外服，多个进程</td></tr>
<tr><td>游戏服</td><td>部署多个游戏服，多个进程</td></tr>
<tr><td>Global服</td><td>可拆分为多个进程，如一个聊天节点、一个邮件节点等等</td></tr>
</tbody>
</table>

<h3>滚服</h3>
如果想做一个滚服的游戏，每个区服有自己的开服时间，做数据隔离，比如一些小游戏。所有服务节点都会连接同一中心服，将对外服和游戏服部署为同一进程，可以在http服实现区服与对外服的关联，
客户端通过http服务获取对外服列表(api:/external_address_list)，返回的数据包含服务器id、协议类型、地址，客户端自己选择连接哪个对外服，对外服收到客户端的登录请求及后续消息，发起rpc调用时，由于本进程内有游戏服节点，会优先本进程处理消息，客户端的消息永远在本进程内处理。
每个游戏服可以配置不同的数据库，实现数据隔离。或者如果想使用同一个数据库，那么登录协议中C2S_SelectHuman可以带上服务器id，创建和加载数据表角色列表时（human_list），就会读取此服务器id的数据，也能实现数据隔离。
对于系统级的数据，GameSystemUtils中的数据加载与存储，和ServiceManager中的数据加载与存储。都是带有rpc服务id字段的（也就是配置文件中的rpc.node.server-id），所以本身就是数据隔离的。
<table>
<thead><tr><th>节点类型</th><th>部署方式</th></tr></thead>
<tbody>
<tr><td>中心服</td><td>部署一个中心服，一个进程</td></tr>
<tr><td>HTTP服</td><td>部署一个HTTP服，一个进程</td></tr>
<tr><td>GM后台</td><td>部署一个GM后台服 + gmback-ui（前端）</td></tr>
<tr><td>对外服和游戏服</td><td>部署为同一进程，一个进程就是一个区服</td></tr>
<tr><td>Global服</td><td>可拆分为多个进程，如一个聊天节点、一个邮件节点等等</td></tr>
</tbody>
</table>

<h2>nginx配置</h2>
<p>本地测试一般会直连对外服的 ip+端口，正式上线需要域名与 SSL，由 Nginx 统一入口：WebSocket（<code>/ws/端口</code>）、HTTP 接口（8090）、GM 后台 API（8010）与 gmback-ui 静态页（<code>dist/</code>）。以下示例假设域名为 <code>www.goldminer.cloud</code>，GM 后台构建产物在 <code>/home/sunrise-game-frame/gmback-ui/dist/</code>。</p>

<pre><code>server {
    listen 80;
    server_name goldminer.cloud www.goldminer.cloud;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name goldminer.cloud www.goldminer.cloud;

    ssl_certificate     /etc/nginx/cert/www.goldminer.cloud_bundle.crt;
    ssl_certificate_key /etc/nginx/cert/www.goldminer.cloud.key;

    ssl_protocols       TLSv1 TLSv1.1 TLSv1.2 TLSv1.3;
    ssl_ciphers         ECDHE-RSA-AES128-GCM-SHA256:ECDHE:ECDH:AES:HIGH:!NULL:!aNULL:!MD5:!ADH:!RC4;
    ssl_session_timeout 20m;
    ssl_verify_client off;

    # 动态 WebSocket 端口转发
    # 客户端连接地址示例: wss://www.goldminer.cloud/ws/10001
    location ~ ^/ws/(?<forward_port>\\d+)$ {
        rewrite ^/ws/\\d+ / break;
        proxy_pass http://127.0.0.1:\$forward_port;

        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header Host \$host;
    }

    # HTTP 接口转发到 HttpServer（8090）
    location ~ ^/external_address {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # GM 后台 API 转发到 gmback（8010，保留 /api 前缀）
    location /api/ {
        proxy_pass http://127.0.0.1:8010;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # GM 后台静态文件（npm run build 后的 dist/）
    root /home/sunrise-game-frame/gmback-ui/dist;
    index index.html;

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
</code></pre>
`);

registerPage('client-tools', '客户端工具', '消息发送工具、压测机器人、压测统计', () => `
<h1>客户端工具</h1>
<p class="page-desc">项目提供消息发送工具、压测机器人和压测统计工具三种客户端工具</p>

<h2>消息发送工具</h2>
<p>基于 Swing 的 GUI 工具，支持多标签页，每标签页一个玩家连接。自动读取 proto 信息构建发送 UI。</p>

<h3>启动方式</h3>
<pre><code class="language-bash">start\\windows\\client.bat</code></pre>

<h3>核心类</h3>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>ClientStartUp</td><td>启动入口</td></tr>
<tr><td>MainFrame</td><td>主窗口（Swing），支持多标签页</td></tr>
<tr><td>SendMsgFrame</td><td>单玩家消息面板（选择 TOPIC → 选择消息 → 填写字段 → 发送）</td></tr>
<tr><td>MessageUtil</td><td>动态读取 proto 信息，构建 UI 与解析器。init() 自动注册 topic/packet/parser</td></tr>
<tr><td>ProtocolRouter</td><td>根据 packetType+packetId 路由消息到处理器，key = packetType * 100000 + packetId</td></tr>
</tbody>
</table>

<h3>自动协议适配</h3>
<p>只要 .proto 已生成，<code>MessageUtil.init()</code> 会按命名规则自动注册 topic / packet / parser，无需手动配置。UI 中的消息列表和字段输入框都是自动生成的。</p>

<h2>压测机器人</h2>
<p>支持批量创建客户端、自动登录、定时 Ping，统计连接/登录状态。</p>

<h3>启动方式</h3>
<pre><code class="language-bash">start\\windows\\bot.bat</code></pre>

<h3>核心类</h3>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>BotStartUp</td><td>启动入口</td></tr>
<tr><td>BotManager</td><td>批量创建客户端、登录、定时 Ping（每10秒）</td></tr>
<tr><td>BotFrame</td><td>压测 UI（显示统计信息）</td></tr>
</tbody>
</table>

<h3>BotManager 功能</h3>
<table>
<thead><tr><th>方法</th><th>说明</th></tr></thead>
<tbody>
<tr><td>addBots(count)</td><td>批量创建机器人（异步登录）</td></tr>
<tr><td>removeBots(count)</td><td>批量移除</td></tr>
<tr><td>removeBot(uid)</td><td>移除单个</td></tr>
<tr><td>stopAll()</td><td>停止所有</td></tr>
<tr><td>sendToAllBots(topic, packetId, data, interval, times)</td><td>向所有在线机器人发送消息</td></tr>
</tbody>
</table>

<h3>BotManager.Stats 统计</h3>
<table>
<thead><tr><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>total</td><td>总创建数</td></tr>
<tr><td>connected</td><td>已连接数</td></tr>
<tr><td>loginSuccess</td><td>登录成功数</td></tr>
<tr><td>loginFailed</td><td>登录失败数</td></tr>
<tr><td>disconnected</td><td>已断开数</td></tr>
</tbody>
</table>

<h2>压测统计工具</h2>
<p>独立 GUI（<code>sunrise-stress.jar</code>）：分阶段统计「取地址 / 登录选角」耗时，并以<strong>服务器回包</strong>为准测量发包 TPS；内置客户端/服务端诊断日志对照。详见 <a href="#/stress-testing">压测统计文档</a>。</p>

<h3>启动方式</h3>
<pre><code class="language-bash">start\\windows\\stress.bat</code></pre>

<h3>核心类</h3>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>StressStartUp</td><td>启动入口</td></tr>
<tr><td>StressManager</td><td>分阶段登录、发包会话、TPS 与流控（在途上限 64）</td></tr>
<tr><td>StressFrame</td><td>压测 UI（实时统计 + 发包压测 + 日志）</td></tr>
</tbody>
</table>

<h2>客户端连接流程</h2>
<h3>开发模式（player.auth.enabled=false，默认）</h3>
<ol class="step-list">
    <li>读取 <code>client-config.properties</code> 配置</li>
    <li>HTTP 请求 <code>/server_status?uid=xxx</code> 检查服务器状态</li>
    <li>HTTP 请求 <code>/external_address?type=tcp&uid=xxx</code> 获取对外服地址</li>
    <li>根据配置选择 TCP/WebSocket/KCP 连接 ExternalServer</li>
    <li>发送认证消息 "CLIENT_CONNECT_"</li>
    <li>发送 <code>C2S_Login(uid)</code> 登录</li>
    <li>自动处理 S2C_Login → C2S_HumanList → C2S_SelectHuman</li>
    <li>登录完成，定时发送 C2S_ClientPing（每10秒）</li>
</ol>

<h3>生产模式（player.auth.enabled=true）</h3>
<ol class="step-list">
    <li>调用 Http 服 <code>POST /send_code?email=...</code> 获取邮箱验证码</li>
    <li>注册：<code>POST /register?email=&password=&code=</code>；注册成功后进行登录：<code>POST /login?email=&password=</code> 获取 <code>token</code></li>
    <li>HTTP 请求 <code>/server_status</code>、<code>/external_address</code> 时携带 Header <code>Authorization: &lt;token&gt;</code></li>
    <li>连接对外服后发送 <code>C2S_Login(token)</code>（<code>uid</code> 字段留空）</li>
    <li>后续流程与开发模式相同</li>
</ol>

<h2>客户端网络实现</h2>
<table>
<thead><tr><th>类名</th><th>说明</th><th>Pipeline</th></tr></thead>
<tbody>
<tr><td>SocketClient</td><td>客户端抽象基类，支持 Channel 和 Ukcp</td><td>-</td></tr>
<tr><td>TcpClient</td><td>TCP 客户端实现</td><td>SocketMessageEncoder → SocketMessageDecoder → 业务 Handler</td></tr>
<tr><td>WsClient</td><td>WebSocket 客户端实现</td><td>HttpClientCodec → HttpObjectAggregator → WebSocketClientCompressionHandler → WebSocketMessageCodec → 业务 Handler</td></tr>
<tr><td>KcpClientImpl</td><td>KCP 客户端实现</td><td>基于 Ukcp</td></tr>
<tr><td>SocketClientManager</td><td>客户端管理器，uid → SocketClient</td><td>-</td></tr>
<tr><td>LoginManager</td><td>登录管理器，自动完成登录流程</td><td>-</td></tr>
</tbody>
</table>

<h2>消息处理器</h2>
<p>使用 <code>@Handler</code> 注解路由消息：</p>
<pre><code class="language-java">@Handler(packetType = TOPIC_TYPE_LOGIN_VALUE, packetId = S2C_Login_VALUE)
public static void onLogin(SocketClient client, LoginProto.MS2C_Login data) {
    // 自动请求角色列表
    client.sendMsg(TOPIC_TYPE_LOGIN_VALUE, C2S_HumanList_VALUE, ByteString.EMPTY);
}

@Handler(packetType = TOPIC_TYPE_LOGIN_VALUE, packetId = S2C_HumanList_VALUE)
public static void onHumanList(SocketClient client, LoginProto.MS2C_HumanList data) {
    // 自动选择第一个角色
    client.sendMsg(TOPIC_TYPE_LOGIN_VALUE, C2S_SelectHuman_VALUE,
        LoginProto.MC2S_SelectHuman.newBuilder().setPos(0).setServerId(1).build().toByteString());
}</code></pre>
`);

registerPage('client-example', '客户端示例', '用于连接服务器的一些客户端简单示例', () => `
<h1>客户端示例</h1>
<p class="page-desc">用于连接服务器的一些客户端简单示例</p>

<h2>通信示例</h2>
<p>sunrise-game-frame-client：</p>
<a href="https://gitee.com/lieng618/sunrise-game-frame-client" target="_blank">Godot 版游戏客户端</a>

<p>sunrise-goldminer：</p>
<a href="https://gitee.com/lieng618/sunrise-goldminer" target="_blank">网页版通信客户端</a>

<p>sunrise-goldminer-wx：</p>
<a href="https://gitee.com/lieng618/sunrise-goldminer-wx" target="_blank">微信小游戏版通信客户端</a>

<p>sunrise-client-unity：</p>
<a href="https://gitee.com/lieng618/sunrise-client-unity" target="_blank">unity版通信客户端</a>

<h2>WebSocket通信流程</h2>

<p>从http服务器获取external_address，测试版本可以直接连接ip+port，但正式版本需要使用域名和ssl证书，所以连接的最终地址里，把ip替换为域名，拼接上/ws/port，后端由nginx解析转发给对应的端口。</p>

<pre><code>// 请求 external_address
// 对应 url: https://url/external_address?type=websocket&uid=xxx
const addressUrl = \`https://\${networkConfig.serverdomain}/external_address?type=websocket&uid=xxx\`;
const addressData = await requestAsync(addressUrl);
// 返回的 JSON 结构中有 address 字段，格式如 "192.168.1.5:10001"
// {"address":"49.232.236.230:10000"}
const rawAddress = addressData.address;
const parts = rawAddress.split(':');
const targetPort = parts[1]; // 拿到对外服端口
// 正式上线需要域名，所以获取的ip+端口是没法用的
// 所以ip使用域名，拼接上/ws/port，后端会由nginx解析转发到对应的端口
const wsUrl = \`wss://\${networkConfig.serverdomain}/ws/\${targetPort}\`;
</code></pre>

`);

registerPage('api-reference', 'API 参考', 'RPC 服务 API、HTTP 接口、注解参考', () => `
<h1>API 参考</h1>
<p class="page-desc">RPC 服务 API 和 HTTP 接口的完整参考</p>

<h2>RPC 服务完整列表</h2>
<table>
<thead><tr><th>CallEnum</th><th>所在进程</th><th>说明</th></tr></thead>
<tbody>
<tr><td>ExternalRecvGameMessageService_recvMessage</td><td>External</td><td>接收 Game 服消息转发给客户端</td></tr>
<tr><td>FriendRpcListenService_onFriendDeleted</td><td>Game</td><td>好友删除通知（双向）</td></tr>
<tr><td>FriendRpcListenService_onNewFriendRequest</td><td>Game</td><td>新好友申请通知</td></tr>
<tr><td>FriendRpcListenService_onFriendAdded</td><td>Game</td><td>好友添加通知（双向）</td></tr>
<tr><td>GameRecvExternalMessageService_recvMessage</td><td>Game</td><td>接收客户端消息（External 转发）</td></tr>
<tr><td>GameRecvGmBackMessageService_recvMessage</td><td>Game</td><td>接收 GM 命令</td></tr>
<tr><td>GameRpcListenService_sendToHuman</td><td>Game</td><td>发送消息给指定玩家（广播到所有Game服，各自判断是否持有目标玩家）</td></tr>
<tr><td>GameRpcListenService_sendToAllHuman</td><td>Game</td><td>广播消息给所有玩家</td></tr>
<tr><td>GlobalChatService_chat</td><td>Global</td><td>发送聊天消息（最多50条历史）</td></tr>
<tr><td>GlobalChatService_history</td><td>Global</td><td>获取聊天历史（返回 proto 二进制）</td></tr>
<tr><td>GlobalFriendService_getFriends</td><td>Global</td><td>获取好友列表</td></tr>
<tr><td>GlobalFriendService_handleFriendRequest</td><td>Global</td><td>处理好友申请（同意/拒绝）</td></tr>
<tr><td>GlobalFriendService_deleteFriend</td><td>Global</td><td>删除好友</td></tr>
<tr><td>GlobalFriendService_getFriendRequests</td><td>Global</td><td>获取好友申请列表</td></tr>
<tr><td>GlobalFriendService_sendFriendRequest</td><td>Global</td><td>发送好友申请</td></tr>
<tr><td>GlobalMailService_deleteMail</td><td>Global</td><td>删除邮件</td></tr>
<tr><td>GlobalMailService_sendMail</td><td>Global</td><td>发送单人邮件</td></tr>
<tr><td>GlobalMailService_readMail</td><td>Global</td><td>读取邮件</td></tr>
<tr><td>GlobalMailService_receiveMailAttachment</td><td>Global</td><td>领取邮件附件</td></tr>
<tr><td>GlobalMailService_getPlayerMails</td><td>Global</td><td>获取邮件列表</td></tr>
<tr><td>GlobalMailService_sendMailToAll</td><td>Global</td><td>全服发送邮件</td></tr>
<tr><td>GlobalMailService_sendMailToMultiple</td><td>Global</td><td>群发邮件</td></tr>
<tr><td>GlobalPlayerInfoService_updatePlayerInfo</td><td>Global</td><td>更新玩家信息</td></tr>
<tr><td>GlobalPlayerInfoService_getPlayerInfos</td><td>Global</td><td>批量获取玩家信息</td></tr>
<tr><td>GlobalPlayerInfoService_getPlayerInfo</td><td>Global</td><td>获取单个玩家信息</td></tr>
<tr><td>GlobalPlayerInfoService_getAllPlayerIds</td><td>Global</td><td>获取所有角色 ID</td></tr>
<tr><td>GmBackRecvMessageService_recvMessage</td><td>GmBack</td><td>GM 后台消息接收</td></tr>
<tr><td>HttpRecvMessageService_updateExternalRemoteData</td><td>Http</td><td>更新对外服地址数据</td></tr>
<tr><td>HttpRecvMessageService_setExternalServerStatus</td><td>Http</td><td>设置服务器开关</td></tr>
<tr><td>HttpRecvMessageService_setWhitelist</td><td>Http</td><td>设置白名单</td></tr>
<tr><td>HttpRecvMessageService_setAnnouncements</td><td>Http</td><td>设置当前生效公告列表</td></tr>
</tbody>
</table>

<h2>HTTP 接口</h2>
<h3>HttpServer（端口 8090）</h3>
<p>认证接口详见 <a href="#/http-server">HTTP 服务</a>。常用接口：</p>
<table>
<thead><tr><th>接口</th><th>方法</th><th>参数</th><th>返回</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/send_code</td><td>POST</td><td>email</td><td>{result}</td><td>发送邮箱验证码</td></tr>
<tr><td>/register</td><td>POST</td><td>email, password, code</td><td>{result, msg?}</td><td>邮箱注册</td></tr>
<tr><td>/login</td><td>POST</td><td>email, password</td><td>{result, token?}</td><td>邮箱登录，返回 JWT</td></tr>
<tr><td>/forgot_password</td><td>POST</td><td>email, password, code</td><td>{result}</td><td>重置密码</td></tr>
<tr><td>/server_status</td><td>GET</td><td>uid 或 Authorization</td><td>{open}</td><td>服务器状态</td></tr>
<tr><td>/external_address</td><td>GET</td><td>type, uid 或 Authorization</td><td>{address}</td><td>分配对外服地址</td></tr>
<tr><td>/external_address_list</td><td>GET</td><td>-</td><td>[addresses]</td><td>所有对外服地址</td></tr>
<tr><td>/kcp_conv</td><td>GET</td><td>-</td><td>{conv}</td><td>分配 KCP conv ID</td></tr>
<tr><td>/announcements</td><td>GET</td><td>-</td><td>[{id,title,content,startTime,endTime}]</td><td>获取当前生效公告列表</td></tr>
</tbody>
</table>

<h3>GmBackServer（端口 8010）</h3>
<p>由 gmback-ui 调用的 REST 接口；完整列表与鉴权说明见 <a href="#/gmback-server">GM 后台 → REST API</a>。常用接口：</p>
<table>
<thead><tr><th>接口</th><th>方法</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/api/login</td><td>POST</td><td>登录，返回 JWT</td></tr>
<tr><td>/api/auth/info</td><td>GET</td><td>会话与页面权限</td></tr>
<tr><td>/api/nodes</td><td>GET</td><td>节点监控</td></tr>
<tr><td>/api/config/reload</td><td>POST</td><td>热更 Luban 配置</td></tr>
<tr><td>/api/hotswap/jar</td><td>POST</td><td>代码热更 JAR</td></tr>
<tr><td>/api/gm/send-mail</td><td>POST</td><td>发送邮件</td></tr>
<tr><td>/api/gm/kick</td><td>POST</td><td>踢人下线</td></tr>
<tr><td>/api/ban、/api/unban、/api/ban/list</td><td>POST/GET</td><td>封禁</td></tr>
<tr><td>/api/mute、/api/unmute、/api/mute/list</td><td>POST/GET</td><td>禁言</td></tr>
<tr><td>/api/online-players</td><td>GET</td><td>在线玩家</td></tr>
<tr><td>/api/server-status</td><td>GET/POST</td><td>服务器开关</td></tr>
<tr><td>/api/whitelist、/api/whitelist/remove</td><td>GET/POST</td><td>白名单</td></tr>
<tr><td>/api/announcements …</td><td>多种</td><td>公告 CRUD</td></tr>
<tr><td>/api/cdk …</td><td>多种</td><td>兑换码 CRUD</td></tr>
<tr><td>/api/users …</td><td>多种</td><td>用户与权限（管理员）</td></tr>
<tr><td>/api/logs</td><td>GET</td><td>操作日志</td></tr>
</tbody>
</table>

<h2>注解参考</h2>
<h3>RPC 注解</h3>
<table>
<thead><tr><th>注解</th><th>作用目标</th><th>说明</th><th>约束</th></tr></thead>
<tbody>
<tr><td>@RpcService</td><td>类</td><td>标记 RPC 服务类</td><td>必须继承 BaseService，构造参数为 String nodeId</td></tr>
<tr><td>@RpcMethod</td><td>方法</td><td>标记 RPC 方法</td><td>方法名生成到 CallEnum，参数顺序决定调用时传参顺序</td></tr>
</tbody>
</table>

<h3>游戏模块注解</h3>
<table>
<thead><tr><th>注解</th><th>作用目标</th><th>说明</th><th>约束</th></tr></thead>
<tbody>
<tr><td>@HumanModule</td><td>类</td><td>标记玩家模块类</td><td>被 ModuleUtils 扫描自动注册</td></tr>
<tr><td>@GameSystem</td><td>类</td><td>标记游戏系统类</td><td>被 GameSystemUtils 扫描自动注册</td></tr>
<tr><td>@MsgHandlerClass</td><td>类</td><td>标记消息处理类</td><td>指定 packetType</td></tr>
<tr><td>@MsgHandlerMethod</td><td>方法</td><td>标记消息处理方法</td><td>指定 packetId，参数为 (HumanObject) 或 (HumanObject, ProtoData)</td></tr>
</tbody>
</table>

<h3>客户端注解</h3>
<table>
<thead><tr><th>注解</th><th>作用目标</th><th>说明</th></tr></thead>
<tbody>
<tr><td>@Handler</td><td>方法</td><td>客户端消息处理注解，指定 packetType 和 packetId</td></tr>
</tbody>
</table>
`);
