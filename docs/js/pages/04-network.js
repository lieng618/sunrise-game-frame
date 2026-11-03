registerPage('network', '网络层', 'Netty Server/Client、编解码、消息体系、数据库', () => `
<h1>网络层</h1>
<p class="page-desc">network 模块是整个框架的底层公共能力模块，所有服务都依赖它。包含网络通信、RPC 框架、数据库访问、配置加载和工具类</p>

<h2>模块组成</h2>
<div class="card-grid">
    <div class="card"><div class="card-icon">🔌</div><div class="card-title">core/</div><div class="card-desc">Netty Server/Client 基础封装、消息体系、编解码器</div></div>
    <div class="card"><div class="card-icon">🔗</div><div class="card-title">rpc/</div><div class="card-desc">RPC 框架（注解/节点管理/调用/服务管理/中心服）</div></div>
    <div class="card"><div class="card-icon">🗄️</div><div class="card-title">db/</div><div class="card-desc">数据库访问（DbService + HikariCP）、实体类</div></div>
    <div class="card"><div class="card-icon">⚙️</div><div class="card-title">config/</div><div class="card-desc">ConfigReader 配置文件加载器</div></div>
    <div class="card"><div class="card-icon">🔧</div><div class="card-title">utils/</div><div class="card-desc">雪花 ID、节点超时常量、EventLoop 与类扫描等通用工具</div></div>
    <div class="card"><div class="card-icon">🔐</div><div class="card-title">jwt/</div><div class="card-desc">JWT、密码哈希、SMTP 验证码；GM 与玩家鉴权配置在不同进程中使用</div></div>
    <div class="card"><div class="card-icon">🧹</div><div class="card-title">graceful/</div><div class="card-desc">按固定阶段排空 RPC、冻结入口、最终存档、关闭 I/O 与 DB</div></div>
</div>

<h2>网络基类</h2>
<h3>BaseServer</h3>
<p>Netty TCP 服务器封装，管理 ServerBootstrap、Channel、消息管理器。</p>
<pre><code class="language-java">public class BaseServer {
    private EventLoopGroup bossGroup;    // 默认 1 线程
    private EventLoopGroup workerGroup;  // 默认 CPU 核心数 * 2 线程
    private BaseMessageManager messageManager;

    public void startListen(String ip, int port) {
        // SO_BACKLOG = 10240；使用 PooledByteBufAllocator
        // TCP_NODELAY / SO_KEEPALIVE = true
        // WriteBufferWaterMark: low=128KB, high=256KB
        // Pipeline: Encoder → Decoder → IdleStateHandler(读空闲 60s)
        //           → PulseHandler → ServerHandler
    }
}</code></pre>
<p>服务端读空闲上限为 <code>RPC_HEARTBEAT_INTERVAL_SECONDS * 6 = 60</code> 秒；RPC 客户端每 10 秒写空闲时发送心跳。</p>

<h3>BaseClient</h3>
<p>Netty TCP 客户端封装，管理 Bootstrap、Channel、连接状态。支持三种构造方式：</p>
<ul>
    <li>无参或指定 nodeId：创建独立 EventLoopGroup 和消息管理器</li>
    <li>指定 group：与同一 RpcNode 的多个远端连接共享 Netty EventLoopGroup</li>
    <li>共享消息管理器：多个 peer BaseClient 把入站消息汇入 RpcServer 的业务队列，由同一 DispatchThread 处理</li>
</ul>
<p>Pipeline: <code>SocketMessageEncoder → SocketMessageDecoder → IdleStateHandler(10秒写空闲) → PulseHandler → ClientHandler</code></p>

<h3>连接握手流程</h3>
<p>所有 RPC 节点之间的连接都遵循统一的首包认证机制：</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ 连接建立后自动发送 →</span>
        <span class="flow-node flow-node-success">"CLIENT_CONNECT_&lt;nodeId&gt;" 或 "CLIENT_CONNECT_&lt;nodeId&gt;|&lt;rpc-token&gt;"</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-success">Server</span>
        <span class="flow-arrow">→ BaseServerHandler 验证首包 →</span>
        <span class="flow-node flow-node-success">提取 connectNode →</span>
        <span class="flow-arrow">→ 回复 →</span>
        <span class="flow-node flow-node-primary">"CLIENT_CONNECT_RESPONSE_SUCCESS&lt;serverNodeId&gt;" / "...FAILED"</span>
    </div>
</div>
<p>握手完成后，Server 端触发 <code>onRecvConnect()</code> 钩子（RPC 服务端会推送自身支持的方法列表），Client 端标记连接完成。相同 nodeId 已有活跃连接时，新连接会收到 FAILED 并被关闭；配置了 <code>rpc.auth-token</code> 时 token 不匹配会直接断开。</p>

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
<tr><td>DispatchThread</td><td>调度线程，5ms 间隔循环执行心跳，单线程处理业务</td></tr>
<tr><td>pulseHandlerOne()</td><td>子类实现，处理收到的消息</td></tr>
<tr><td>pulseSenderOne()</td><td>子类实现，发送待发消息</td></tr>
</tbody>
</table>
<p>停机时管理器按 <code>RUNNING → DRAINING → FINALIZING → STOPPED</code> 单向流转：先冻结接收入口，继续处理冻结前积压的消息，再在业务 DispatchThread 完成最终快照，最后停止调度和网络资源。队列当前使用 <code>ConcurrentLinkedQueue</code>，没有容量上限，生产业务需要自行控制上游流量。</p>

<h2>编解码器</h2>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>SocketMessageEncoder</td><td>编码器：写入 messageType(4B) + dataLength(4B) + data</td></tr>
<tr><td>SocketMessageDecoder</td><td>拆包处理，最大 data 长度 1MB（<code>Utils.MAX_BODY_SIZE</code>），超限关闭连接</td></tr>
<tr><td>WebSocketMessageCodec</td><td>WebSocket 二进制帧与 SocketMessage 互转，格式与 TCP 相同，继承 MessageToMessageCodec</td></tr>
</tbody>
</table>

<h2>序列化</h2>
<table>
<thead><tr><th>场景</th><th>序列化方式</th><th>工具类</th></tr></thead>
<tbody>
<tr><td>RPC 消息（Call / BaseMessage）</td><td>MessagePack</td><td>禁用了 Jackson 默认多态类型，并用 <code>RpcDataSanitizer</code> 限制为基本类型、String、byte[]、List、Map 等</td></tr>
<tr><td>客户端业务消息</td><td>Protobuf</td><td><code>TopicProto.MBasePacketData.packet_data</code> 内嵌具体业务消息</td></tr>
<tr><td>玩家存档</td><td>JSON 字符串树</td><td><code>human_info.role_data</code> 保存“模块名 → 模块 dataMap”的 JSON，MEDIUMBLOB</td></tr>
<tr><td>RPC 服务存档</td><td>JSON</td><td><code>server_data.data</code> 保存 <code>dataMap</code></td></tr>
</tbody>
</table>

<h2>数据库服务 DbService</h2>
<p>基于 HikariCP 连接池封装，从 ConfigReader 读取 jdbc 配置。提供同步和异步 CRUD 操作：</p>
<p>参数必须是完整 SQL；方法签名中的 <code>params</code> 是 JDBC 占位符参数。异步方法在数据库线程池执行，回调不会自动切回游戏业务线程；游戏代码需要像 <code>LoginMsgHandler</code> 那样通过 <code>AsyncEventManager</code> 再投递回业务线程。</p>
<pre><code class="language-java">DbService db = DbManager.getDbService();

// 同步查询
Map&lt;String, Object&gt; account = db.queryGetOneByParams(
    "select * from \`account\` where \`uid\` = ?", uid);
List&lt;Map&lt;String, Object&gt;&gt; list = db.queryGetAllByParams(
    "select * from \`human_list\` where \`uid\` = ?", uid);
List&lt;String&gt; ids = db.queryAllSingleColumn(
    "select \`human_id\` from \`human_list\`");

// 同步写入
db.execute("update \`account\` set \`update_time\` = now() where \`uid\` = ?", uid);
Object id = db.executeWithGeneratedKey(
    "insert into \`account\` (\`uid\`) values (?)", uid);

// 异步查询：回调运行在 DbService 线程池
db.queryGetOneByParamsAsync(result -&gt; {
    AsyncEventManager.addAsyncEvent(() -&gt; {
        // 这里才回到游戏业务线程
    });
}, "select * from \`human_info\` where \`human_id\` = ?", humanId);

// 异步写入
db.executeAsync(
    "update \`human_info\` set \`role_data\` = ? where \`human_id\` = ?",
    data, humanId);</code></pre>

<h2>工具类</h2>
<table>
<thead><tr><th>类名</th><th>说明</th><th>关键方法/字段</th></tr></thead>
<tbody>
<tr><td>ConfigReader</td><td>加载 properties 并解析 <code>\${ENV:default}</code></td><td>loadConfig(path), getInt/getBoolean/getString, resolvePath</td></tr>
<tr><td>IdGenerator</td><td>基于 yitter 的雪花 ID</td><td>WorkerId = serverId；另提供账号 UID 生成</td></tr>
<tr><td>Utils</td><td>通用工具类</td><td>CLIENT_CONNECT 协议、Epoll/KQueue/NIO 自动选择、类扫描、内存日志</td></tr>
<tr><td>LogCore</td><td><code>log</code> 包中的 Logger 常量</td><td>RpcClient/RpcServer/DB/GameServer/ExternalServer 等</td></tr>
<tr><td>JwtUtil</td><td>JWT 工具类</td><td>HS256；支持用户级 Token 黑名单。独立进程分别初始化自己的密钥；RunAllOne 当前共用同一静态实例，最后执行的非空初始化会覆盖前一次密钥</td></tr>
<tr><td>MailUtil</td><td>邮箱验证码</td><td>SMTP 发送 6 位验证码（5 分钟有效，1 分钟限发一次），verifyCode/removeCode</td></tr>
<tr><td>PasswordUtil</td><td>密码工具</td><td>SHA-256 哈希，encryptPassword/verifyPassword</td></tr>
</tbody>
</table>
`);
