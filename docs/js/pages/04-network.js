registerPage('network', '网络层', 'Netty Server/Client、编解码、消息体系、数据库', () => `
<h1>网络层</h1>
<p class="page-desc">network 模块是整个框架的底层公共能力模块，所有服务都依赖它。包含网络通信、RPC 框架、数据库访问、配置加载和工具类</p>

<h2>模块组成</h2>
<div class="card-grid">
    <div class="card"><div class="card-icon">🔌</div><div class="card-title">core/</div><div class="card-desc">Netty Server/Client 基础封装、消息体系、编解码器</div></div>
    <div class="card"><div class="card-icon">🔗</div><div class="card-title">rpc/</div><div class="card-desc">自研 RPC 框架（注解/节点管理/调用/服务管理/中心服）</div></div>
    <div class="card"><div class="card-icon">🗄️</div><div class="card-title">db/</div><div class="card-desc">数据库访问（DbService + HikariCP）、实体类</div></div>
    <div class="card"><div class="card-icon">⚙️</div><div class="card-title">config/</div><div class="card-desc">ConfigReader 配置文件加载器</div></div>
    <div class="card"><div class="card-icon">🔧</div><div class="card-title">utils/</div><div class="card-desc">日志(LogCore)、ID生成(IdGenerator/yitter)、类扫描(Utils)、JWT(JwtUtil)</div></div>
</div>

<h2>网络基类</h2>
<h3>BaseServer</h3>
<p>Netty TCP 服务器封装，管理 ServerBootstrap、Channel、消息管理器。</p>
<pre><code class="language-java">public class BaseServer {
    private EventLoopGroup bossGroup;    // 默认 1 线程
    private EventLoopGroup workerGroup;  // 默认 CPU 核心数 * 2 线程
    private BaseMessageManager messageManager;

    public void start(int port) {
        // 配置 ServerBootstrap
        // SO_BACKLOG = 10240
        // PooledByteBufAllocator
        // TCP_NODELAY = true
        // SO_KEEPALIVE = true
        // WriteBufferWaterMark: low=128KB, high=256KB
        // Pipeline: SocketMessageEncoder → SocketMessageDecoder → PulseHandler → ServerHandler
    }
}</code></pre>

<h3>BaseClient</h3>
<p>Netty TCP 客户端封装，管理 Bootstrap、Channel、连接状态。支持三种构造方式：</p>
<ul>
    <li>独立 EventLoopGroup（单连接场景）</li>
    <li>共享 group + b（多个连接共享线程池，如 RpcNode 连接多个远端节点）</li>
</ul>
<p>Pipeline: <code>SocketMessageEncoder → SocketMessageDecoder → IdleStateHandler(10秒写空闲) → PulseHandler → ClientHandler</code></p>

<h3>连接握手流程</h3>
<p>所有 RPC 节点之间的连接都遵循统一的首包认证机制：</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ 连接建立后自动发送 →</span>
        <span class="flow-node flow-node-success">"CLIENT_CONNECT_&lt;nodeId&gt;"</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-success">Server</span>
        <span class="flow-arrow">→ BaseServerHandler 验证首包 →</span>
        <span class="flow-node flow-node-success">提取 connectNode →</span>
        <span class="flow-arrow">→ 回复 →</span>
        <span class="flow-node flow-node-primary">"CLIENT_CONNECT_RESPONSE_SUCCESS&lt;serverNodeId&gt;"</span>
    </div>
</div>
<p>握手完成后，Server 端触发 <code>onRecvConnect()</code> 钩子（RPC 服务端会推送自身支持的方法列表），Client 端标记连接完成并触发 <code>onRecvConnect()</code> 钩子。</p>

<h2>消息体系</h2>
<h3>SocketMessage（网络传输层）</h3>
<pre><code class="language-text">┌──────────────┬──────────────┬─────────────────┐
│ messageType  │ dataLength   │     data        │
│  (4 bytes)   │  (4 bytes)   │  (N bytes)      │
└──────────────┴──────────────┴─────────────────┘
messageType: 0 = 心跳, 1 = 业务消息
data: MessagePack 序列化的 BaseMessage / Protobuf 序列化的 MBasePacketData</code></pre>

<h3>BaseMessage（逻辑层）</h3>
<p>所有逻辑消息的基类，RPC 通信使用：</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>nodeId</td><td>String</td><td>来源节点 ID</td></tr>
<tr><td>toNodeId</td><td>String</td><td>目标节点 ID</td></tr>
<tr><td>messageId</td><td>long</td><td>唯一消息 ID（由 IdGenerator 雪花算法生成）</td></tr>
<tr><td>msg</td><td>Object</td><td>消息体</td></tr>
</tbody>
</table>

<h3>消息管理器 BaseMessageManager</h3>
<p>消息管理器基类，维护收发队列，运行 DispatchThread：</p>
<table>
<thead><tr><th>组件</th><th>说明</th></tr></thead>
<tbody>
<tr><td>recvMsgQueue</td><td>接收消息队列，网络线程写入，主线程消费</td></tr>
<tr><td>sendMsgQueue</td><td>发送消息队列，主线程写入，网络线程消费</td></tr>
<tr><td>DispatchThread</td><td>调度线程，5ms 间隔循环执行心跳</td></tr>
<tr><td>pulseHandlerOne()</td><td>子类实现，处理收到的消息</td></tr>
<tr><td>pulseSenderOne()</td><td>子类实现，发送待发消息</td></tr>
</tbody>
</table>

<h2>编解码器</h2>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>SocketMessageEncoder</td><td>编码器：写入 messageType(4B) + dataLength(4B) + data</td></tr>
<tr><td>SocketMessageDecoder</td><td>解码器：拆包处理，最大包体 1MB，继承 ByteToMessageDecoder</td></tr>
<tr><td>WebSocketMessageCodec</td><td>WebSocket 编解码器，格式与 TCP 相同，继承 ByteToMessageCodec</td></tr>
</tbody>
</table>

<h2>序列化</h2>
<table>
<thead><tr><th>场景</th><th>序列化方式</th><th>工具类</th></tr></thead>
<tbody>
<tr><td>RPC 消息（Call 对象）</td><td>MessagePack</td><td>MessageUtils（ObjectMapper + MessagePackFactory）</td></tr>
<tr><td>业务消息（客户端协议）</td><td>Protobuf</td><td>ProtoParserUtils 自动注册 parseFrom</td></tr>
<tr><td>存档数据</td><td>FastJSON</td><td>角色数据 JSON 存入 MEDIUMBLOB</td></tr>
</tbody>
</table>

<h2>数据库服务 DbService</h2>
<p>基于 HikariCP 连接池封装，从 ConfigReader 读取 jdbc 配置。提供同步和异步 CRUD 操作：</p>
<pre><code class="language-java">DbService dbService = DbManager.getDbService();

// ===== 同步查询 =====
List&lt;Map&lt;String, Object&gt;&gt; result = dbService.queryAll("account");
Map&lt;String, Object&gt; one = dbService.queryById("account", accountId, "id");
List&lt;Map&lt;String, Object&gt;&gt; list = dbService.queryGetAllByParams("human_list", "uid", uid);

// ===== 异步查询（回调在主线程执行）=====
dbService.queryGetAllByParamsAsync("human_list", "uid", uid, results -> {
    // 处理结果
});

// ===== 同步执行 =====
dbService.execute("INSERT INTO account (uid) VALUES (?)", uid);
int id = dbService.executeWithGeneratedKey("INSERT INTO human_list ...", params);

// ===== 异步执行 =====
dbService.executeAsync("UPDATE human_info SET role_data = ? WHERE human_id = ?", data, humanId);
dbService.executeAsync(callback, "DELETE FROM human_list WHERE human_id = ?", humanId);</code></pre>

<h2>工具类</h2>
<table>
<thead><tr><th>类名</th><th>说明</th><th>关键方法/字段</th></tr></thead>
<tbody>
<tr><td>ConfigReader</td><td>配置文件读取器，加载 .properties 文件</td><td>loadConfig(name), getInt(key), getStr(key)</td></tr>
<tr><td>IdGenerator</td><td>雪花 ID 生成器，基于 yitter</td><td>WorkerId = serverId，生成全局唯一 long ID</td></tr>
<tr><td>Utils</td><td>通用工具类</td><td>getLocalIp(), createEventLoopGroup(自动选择 Epoll/KQueue/NIO), scanPackage(包扫描), checkMemory(内存检测)</td></tr>
<tr><td>LogCore</td><td>日志常量类</td><td>定义各模块 Logger（CENTER/RPC/DB/GAME/EXTERNAL 等）</td></tr>
<tr><td>JwtUtil</td><td>JWT 工具类</td><td>HS256 签名，generateToken/parseToken，支持 Token 黑名单</td></tr>
</tbody>
</table>
`);
