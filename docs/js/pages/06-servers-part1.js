registerPage('center-server', '中心服', '节点注册中心、发现与广播', () => `
<h1>中心服</h1>
<p class="page-desc">CenterServer 是所有 RPC 节点的注册中心，负责节点发现与信息广播，不执行任何业务 RPC，只做"节点通讯录同步"。各节点上报时携带 <code>nodeType</code>，中心服根据 <code>RpcConnectPolicy</code>（<code>center-config.properties</code> 中的 <code>rpc.connect.*</code>）决定向谁广播对端地址；未配置策略时保持全量互连。另提供 HTTP Dashboard 实时查看在线节点与 RPC 连接拓扑。
中心服挂掉不影响已互连的节点间通信，但新节点无法加入。支持断线重连，中心服重新启动后，所有节点都会重新注册。</p>
<p>目前最大支持4096个节点服务id，服务id的上限受限于雪花算法生成器控制，可根据需求进行配置，目前为：</p>
<pre><code class="language-java">public static void init(int WorkerId) {
    if (options == null) {
        options = new IdGeneratorOptions((short) WorkerId); // 节点id 限制为1-4096
        options.WorkerIdBitLength = 12; // 2^12-1，即最多支持4096个节点。
        options.SeqBitLength = 10; // 限制每毫秒生成的ID个数。若生成速度超过5万个/秒，建议加大 SeqBitLength 到 10。
        options.BaseTime = 1727712000000L; // 基础时间，设定为2024-10-01 00:00:00
        YitIdHelper.setIdGenerator(options);
        LogCore.RpcUtils.info("IdGenerator init, WorkerId = { {} }", WorkerId);
    }
}</code></pre>
<p> 为了方便管理，将 <strong>serverId</strong> 进行分组，并在配置中用 <strong>nodeType</strong> 标识进程角色（用于连接策略）：中心服 id=1；GM 后台 <code>gmback</code> 默认 2；Http <code>http</code> 默认 3（预留到 99）；对外服 <code>external</code> 默认 100（预留到 199）；游戏服 <code>game</code> 默认 200（预留到 3999）；全局服 <code>global</code> 默认 4000（预留到 4096）。id 与 type 均可按需求调整，详见 <a href="#/config">配置参考</a>。</p>
<h2>核心职责</h2>
<ul>
    <li>接收各节点上报（ip/port/serverId/nodeId/nodeType）</li>
    <li>维护当前在线节点信息（datasByNodeId Map）</li>
    <li>新节点加入时，按连接策略将符合条件的节点地址双向广播</li>
    <li>让各 RPC 节点按策略建立出站连接</li>
    <li>节点失效检测（2 倍上报间隔无上报视为失效）</li>
    <li>可选：启动 Dashboard，提供 RPC 拓扑可视化</li>
</ul>

<h2>核心类</h2>
<table>
<thead><tr><th>类名</th><th>说明</th><th>关键方法/字段</th></tr></thead>
<tbody>
<tr><td>CenterServer</td><td>封装 BaseServer，使用 CenterServerMessageManager</td><td>构造(id,ip,port)，start()</td></tr>
<tr><td>CenterServerManager</td><td>中心服单例管理器</td><td>createCenterServer(), getCenterServerNodeId()</td></tr>
<tr><td>CenterServerMessageManager</td><td>收到消息后调用 NodeManager.updateNode()</td><td>pulseHandlerOne() 反序列化 BaseMessage</td></tr>
<tr><td>NodeManager</td><td>维护所有 RPC 节点信息</td><td>updateNode(), broadcastToNode(), isNodeDead(), reportFull(), reportSimple()</td></tr>
<tr><td>NodeData</td><td>节点数据</td><td>nodeId, ip, port, reportTime, serverId, nodeType</td></tr>
<tr><td>RpcConnectPolicy</td><td>RPC 连接策略（读取 center-config）</td><td>init(), shouldConnect(fromType, toType)</td></tr>
<tr><td>CenterDashboardServer</td><td>拓扑可视化 HTTP 服务（Javalin）</td><td>GET / ，GET /api/topology</td></tr>
</tbody>
</table>

<h2>启动流程</h2>
<p>启动类仅接收配置文件路径，<code>master.id</code>、Dashboard 端口等均从配置读取：</p>
<pre><code class="language-java">public class CenterServerStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] { "./config/center-config.properties" };
        }
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        System.setProperty("programName", "CenterServer-" + properties.getProperty("master.id"));
        Utils.setLogLevel(properties.getProperty("log.level"));

        var centerServer = CenterServerManager.createCenterServer(
            Integer.parseInt(properties.getProperty("master.id")),
            properties.getProperty("master.address"),
            Integer.parseInt(properties.getProperty("master.port"))
        );
        centerServer.start(); // 内部 RpcConnectPolicy.init()

        int dashboardPort = Integer.parseInt(properties.getProperty("dashboard.port", "8088"));
        if (dashboardPort > 0) {
            new CenterDashboardServer(dashboardPort).start();
        }
    }
}</code></pre>
<p>命令行示例：<code>java -jar sunrise-center.jar config/center-config.properties</code></p>

<h2>节点上报机制</h2>
<h3>全量上报（首次连接）</h3>
<p>ReportClient 连接成功后，ReportClientHandler.onConnectSuccess() 调用 NodeManager.reportFull()，上报完整的 ip/port/serverId/nodeId/nodeType 信息。</p>

<h3>简易心跳上报（每3秒）</h3>
<p>ReportClientMessageManager.pulseReport() 每 3 秒调用 NodeManager.reportSimple()，仅发送 nodeId 保持心跳。</p>

<h3>节点失效检测</h3>
<p>NodeManager 维护每个节点的 <code>reportTime</code>，<code>isNodeDead()</code> 判断超过 2 倍上报间隔（INTERVAL_SIMPLE * 2 = 6秒）没有收到心跳上报，则视为节点失效，从 datasByNodeId 中移除。</p>

<h2>配置项</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>master.id</td><td>中心服 ID</td><td>1</td></tr>
<tr><td>master.address</td><td>中心服监听地址</td><td>127.0.0.1</td></tr>
<tr><td>master.port</td><td>中心服监听端口</td><td>8000</td></tr>
<tr><td>dashboard.port</td><td>拓扑 Dashboard 端口；设为 0 关闭</td><td>8088</td></tr>
<tr><td>rpc.connect.*</td><td>各 nodeType 的出站连接白名单，见 <a href="#/rpc">RPC 框架 · 连接策略</a></td><td>rpc.connect.game=external,global,gmback</td></tr>
</tbody>
</table>

<h2>RPC 拓扑 Dashboard</h2>
<p>中心服启动后（<code>dashboard.port &gt; 0</code>），浏览器访问 <code>http://127.0.0.1:8088/</code>（端口以配置为准）。页面定时拉取 <code>GET /api/topology</code>，展示：</p>
<ul>
    <li>当前在线 RPC 节点（serverId、nodeType、地址、心跳状态）</li>
    <li>是否启用连接策略、规则列表</li>
    <li>按策略推导的节点间连接边（有向）</li>
</ul>
`);

registerPage('external-server', '对外服', 'TCP/WS/KCP 网关、客户端连接管理', () => `
<h1>对外服</h1>
<p class="page-desc">ExternalServer 是客户端接入网关，同时支持 TCP、WebSocket、KCP 三种协议，是客户端与服务器之间的唯一入口</p>

<h2>核心职责</h2>
<ul>
    <li>监听 TCP / WebSocket / KCP 端口（端口依次 +1）</li>
    <li>验证客户端首包认证（"CLIENT_CONNECT_"）</li>
    <li>为每个连接分配 connectionId</li>
    <li>消息频率限制（默认1000条/分钟，可在 external-config 中调整）</li>
    <li>将客户端消息转发到对应的 Game 节点（SendDesignated）</li>
    <li>接收 Game 服 RPC 返回，再发回客户端</li>
    <li>每5秒向 HttpServer 上报 External 地址</li>
</ul>

<h2>多协议支持</h2>
<p>ExternalServer 同时启动三种协议监听，共享同一个 ExternalConnectionManager：</p>
<table>
<thead><tr><th>协议</th><th>端口</th><th>Pipeline</th><th>处理器</th></tr></thead>
<tbody>
<tr><td>TCP</td><td>port</td><td>SocketMessageEncoder → SocketMessageDecoder → ExternalServerHandler</td><td>ExternalServerHandler</td></tr>
<tr><td>WebSocket</td><td>port + 1</td><td>HttpServerCodec → HttpObjectAggregator → WebSocketServerCompressionHandler → WebSocketMessageCodec → WebSocketServerProtocolHandler → ExternalServerHandler</td><td>ExternalServerHandler</td></tr>
<tr><td>KCP</td><td>port + 2</td><td>KcpServerHandler</td><td>KcpServerHandler</td></tr>
</tbody>
</table>
<p>端口自动分配：查询 external_system 表，已有记录则复用，否则从 10000 开始递增。</p>

<h2>客户端连接认证</h2>
<p>连接建立后首包不是 Protobuf，而是字符串认证，此字符串定义在Utils中。</p>
<pre><code class="language-text">客户端发送: "CLIENT_CONNECT_"
服务端验证通过后分配 connectionId，创建 ClientConnection 对象</code></pre>

<h2>消息频率限制</h2>
<p>ClientConnection.dataCheck() 内置消息频率限制，定义在Utils中。</p>
<ul>
    <li><strong>频率限制</strong> - 默认每分钟1000条（可在 external-config 中通过 <code>external.rate-limit.per-minute</code> 调整）</li>
</ul>

<h2>External 与 Game 的绑定</h2>
<p>首次 External 向 Game 转发客户端消息时，会把自己的 RPC 节点 ID 一起发过去。首次 Game 向 External 回包时，也会把自己的 RPC 节点 ID 带回。</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-success">External</span>
        <span class="flow-arrow">→ 首次转发带 externalNodeId →</span>
        <span class="flow-node flow-node-secondary">Game</span>
        <span class="flow-arrow">→ 记录到 ClientConnection.gameNodeId →</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">Game</span>
        <span class="flow-arrow">→ 首次回包带 gameNodeId →</span>
        <span class="flow-node flow-node-success">External</span>
        <span class="flow-arrow">→ 记录到 ConnectObject.externalNodeId →</span>
    </div>
</div>
<p>后续消息直接使用绑定的 nodeId 定向发送（SendDesignated），实现双向绑定。</p>

<h2>核心类</h2>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>ExternalServer</td><td>同时启动 TCP/WS/KCP 三种协议监听，查询 external_system 表管理端口</td></tr>
<tr><td>ExternalServerHandler</td><td>TCP/WS 入站处理器，首包验证，后续消息加入 msgQueue</td></tr>
<tr><td>KcpServerHandler</td><td>KCP 入站处理器</td></tr>
<tr><td>ExternalConnectionManager</td><td>客户端连接管理器，维护 clients Map(connectionId → ClientConnection)</td></tr>
<tr><td>ClientConnection</td><td>客户端连接对象，支持 Channel(TCP/WS) 和 Ukcp(KCP)，含消息频率限制</td></tr>
<tr><td>ExternalRecvGameMessageService</td><td>@RpcService，核心服务：消息转发、地址上报</td></tr>
</tbody>
</table>

<h2>ExternalRecvGameMessageService 心跳</h2>
<table>
<thead><tr><th>频率</th><th>行为</th></tr></thead>
<tbody>
<tr><td>pulse()</td><td>遍历所有 ClientConnection，将 msgQueue 中的消息通过 RPC SendDesignated 转发给 Game 服</td></tr>
<tr><td>pulsePer5Sec()</td><td>清理失效客户端（!isActive）+ 向 HttpServer 上报 External 地址</td></tr>
<tr><td>pulsePerMin()</td><td>向 GmBack 上报节点数据（在线连接数等）</td></tr>
</tbody>
</table>

<h2>配置项</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>external.address</td><td>对外暴露的 IP（客户端连接地址）</td><td>127.0.0.1</td></tr>
</tbody>
</table>
`);
