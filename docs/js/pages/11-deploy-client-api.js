registerPage('deployment', '部署指南', '本地部署、Docker 部署、Linux 部署', () => `
<h1>部署指南</h1>
<p class="page-desc">支持本地部署、Docker 部署和 Linux 部署三种方式</p>

<h2>端口说明</h2>
<table>
<thead><tr><th>服务</th><th>端口</th><th>协议</th><th>用途</th></tr></thead>
<tbody>
<tr><td>CenterServer</td><td>8000</td><td>TCP</td><td>RPC 节点注册中心（内部）</td></tr>
<tr><td>ExternalServer</td><td>10000</td><td>TCP</td><td>客户端 TCP 连接</td></tr>
<tr><td>ExternalServer</td><td>10001</td><td>WebSocket</td><td>客户端 WS 连接</td></tr>
<tr><td>ExternalServer</td><td>10002</td><td>KCP</td><td>客户端 KCP 连接</td></tr>
<tr><td>HttpServer</td><td>8090</td><td>HTTP</td><td>地址分发接口</td></tr>
<tr><td>GmBackServer</td><td>8010</td><td>HTTP</td><td>GM 后台管理</td></tr>
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
<tr><td>center</td><td>无</td><td>mysql(healthy)</td><td>中心服</td></tr>
<tr><td>external</td><td>10000:10000, 10001:10001, 10002:10002</td><td>mysql(healthy), center</td><td>对外服</td></tr>
<tr><td>game</td><td>无</td><td>mysql(healthy), center</td><td>游戏服</td></tr>
<tr><td>global</td><td>无</td><td>mysql(healthy), center</td><td>全局服</td></tr>
<tr><td>http</td><td>8090:8090</td><td>mysql(healthy), center</td><td>HTTP 服务</td></tr>
<tr><td>gmback</td><td>8010:8010</td><td>mysql(healthy), center</td><td>GM 后台</td></tr>
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
`);

registerPage('client-tools', '客户端工具', '消息发送工具、压测机器人', () => `
<h1>客户端工具</h1>
<p class="page-desc">项目提供消息发送工具和压测机器人两种客户端工具</p>

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

<h2>客户端连接流程</h2>
<ol class="step-list">
    <li>读取 <code>client-config.properties</code> 配置</li>
    <li>HTTP 请求 HttpServer:/server_status 检查服务器状态</li>
    <li>HTTP 请求 HttpServer:/external_address 获取对外服地址</li>
    <li>根据配置选择 TCP/WebSocket/KCP 连接 ExternalServer</li>
    <li>发送认证消息 "CLIENT_CONNECT_"</li>
    <li>发送 C2S_Login(uid) 登录</li>
    <li>自动处理 S2C_Login → 请求角色列表(C2S_HumanList) → 选择角色(C2S_SelectHuman)</li>
    <li>登录完成，定时发送 C2S_ClientPing（每10秒）</li>
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

registerPage('api-reference', 'API 参考', 'RPC 服务 API、HTTP 接口、注解参考', () => `
<h1>API 参考</h1>
<p class="page-desc">RPC 服务 API 和 HTTP 接口的完整参考</p>

<h2>RPC 服务完整列表</h2>
<table>
<thead><tr><th>ID</th><th>CallEnum</th><th>所在进程</th><th>说明</th></tr></thead>
<tbody>
<tr><td>1</td><td>ChatRpcListenService_onChat</td><td>Game</td><td>聊天消息广播到本服玩家</td></tr>
<tr><td>2</td><td>ChatService_chat</td><td>Global</td><td>发送聊天消息（最多50条历史）</td></tr>
<tr><td>3</td><td>ChatService_history</td><td>Global</td><td>获取聊天历史（返回 proto 二进制）</td></tr>
<tr><td>4</td><td>ExternalRecvMessageService_recvMessage</td><td>External</td><td>接收 Game 服消息转发给客户端</td></tr>
<tr><td>5</td><td>FriendRpcListenService_onNewFriendRequest</td><td>Game</td><td>新好友申请通知</td></tr>
<tr><td>6</td><td>FriendRpcListenService_onFriendAdded</td><td>Game</td><td>好友添加通知（双向）</td></tr>
<tr><td>7</td><td>FriendRpcListenService_onFriendDeleted</td><td>Game</td><td>好友删除通知（双向）</td></tr>
<tr><td>8</td><td>FriendService_handleFriendRequest</td><td>Global</td><td>处理好友申请（同意/拒绝）</td></tr>
<tr><td>9</td><td>FriendService_getFriends</td><td>Global</td><td>获取好友列表</td></tr>
<tr><td>10</td><td>FriendService_sendFriendRequest</td><td>Global</td><td>发送好友申请</td></tr>
<tr><td>11</td><td>FriendService_deleteFriend</td><td>Global</td><td>删除好友</td></tr>
<tr><td>12</td><td>FriendService_getFriendRequests</td><td>Global</td><td>获取好友申请列表</td></tr>
<tr><td>13</td><td>GameRecvGmBackService_recvMessage</td><td>Game</td><td>接收 GM 命令</td></tr>
<tr><td>14</td><td>GameRecvMessageService_recvMessage</td><td>Game</td><td>接收客户端消息（External 转发）</td></tr>
<tr><td>15</td><td>GmBackRecvMessageService_recvMessage</td><td>GmBack</td><td>GM 后台消息接收</td></tr>
<tr><td>16</td><td>HttpRecvMessageService_updateExternalRemoteData</td><td>Http</td><td>更新对外服地址数据</td></tr>
<tr><td>17</td><td>HttpRecvMessageService_setExternalServerStatus</td><td>Http</td><td>设置服务器开关</td></tr>
<tr><td>18</td><td>HttpRecvMessageService_setWhitelist</td><td>Http</td><td>设置白名单</td></tr>
<tr><td>19</td><td>MailRpcListenService_onNewMail</td><td>Game</td><td>新邮件通知</td></tr>
<tr><td>20</td><td>MailService_claimAttachment</td><td>Global</td><td>领取邮件附件</td></tr>
<tr><td>21</td><td>MailService_sendMail</td><td>Global</td><td>发送单人邮件</td></tr>
<tr><td>22</td><td>MailService_readMail</td><td>Global</td><td>读取邮件</td></tr>
<tr><td>23</td><td>MailService_deleteMail</td><td>Global</td><td>删除邮件</td></tr>
<tr><td>24</td><td>MailService_sendGroupMail</td><td>Global</td><td>群发邮件</td></tr>
<tr><td>25</td><td>MailService_sendAllMail</td><td>Global</td><td>全服发送邮件</td></tr>
<tr><td>26</td><td>MailService_getMailList</td><td>Global</td><td>获取邮件列表</td></tr>
<tr><td>27</td><td>PlayerInfoService_getPlayerInfo</td><td>Global</td><td>获取单个玩家信息</td></tr>
<tr><td>28</td><td>PlayerInfoService_updatePlayerInfo</td><td>Global</td><td>更新玩家信息</td></tr>
<tr><td>29</td><td>PlayerInfoService_getPlayerInfos</td><td>Global</td><td>批量获取玩家信息</td></tr>
<tr><td>30</td><td>PlayerInfoService_getAllHumanIds</td><td>Global</td><td>获取所有角色 ID</td></tr>
</tbody>
</table>

<h2>HTTP 接口</h2>
<h3>HttpServer（端口 8090）</h3>
<table>
<thead><tr><th>接口</th><th>方法</th><th>参数</th><th>返回</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/server_status</td><td>GET</td><td>uid</td><td>{open}</td><td>服务器状态</td></tr>
<tr><td>/external_address</td><td>GET</td><td>type, uid</td><td>{address}</td><td>分配对外服地址</td></tr>
<tr><td>/external_address_list</td><td>GET</td><td>-</td><td>{addresses}</td><td>所有对外服地址</td></tr>
<tr><td>/kcp_conv</td><td>GET</td><td>-</td><td>{conv}</td><td>分配 KCP conv ID</td></tr>
</tbody>
</table>

<h3>GmBackServer（端口 8010）</h3>
<table>
<thead><tr><th>接口</th><th>方法</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/api/login</td><td>POST</td><td>登录认证，返回 JWT Token</td></tr>
<tr><td>/api/nodes</td><td>GET</td><td>节点监控</td></tr>
<tr><td>/api/gm/send-mail</td><td>POST</td><td>发送邮件</td></tr>
<tr><td>/api/gm/kick</td><td>POST</td><td>踢人下线</td></tr>
<tr><td>/api/gm/reload-config</td><td>POST</td><td>热更配置</td></tr>
<tr><td>/api/ban/add</td><td>POST</td><td>添加封禁</td></tr>
<tr><td>/api/ban/remove</td><td>POST</td><td>解除封禁</td></tr>
<tr><td>/api/ban/list</td><td>GET</td><td>封禁列表</td></tr>
<tr><td>/api/mute/add</td><td>POST</td><td>添加禁言</td></tr>
<tr><td>/api/mute/remove</td><td>POST</td><td>解除禁言</td></tr>
<tr><td>/api/mute/list</td><td>GET</td><td>禁言列表</td></tr>
<tr><td>/api/online-players</td><td>GET</td><td>在线玩家</td></tr>
<tr><td>/api/server-status</td><td>GET/POST</td><td>服务器开关</td></tr>
<tr><td>/api/whitelist</td><td>GET/POST</td><td>白名单管理</td></tr>
<tr><td>/api/users</td><td>GET</td><td>用户管理</td></tr>
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
