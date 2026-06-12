registerPage('rpc', 'RPC 框架', '节点发现、方法注册、调用模式、回调超时', () => `
<h1>RPC 框架</h1>
<p class="page-desc">基于注解的远程方法调用系统，是整个框架的核心通信机制。支持节点发现、方法注册、三种调用模式（随机/广播/定向）、回调与超时机制</p>

<h2>核心角色</h2>
<div class="card-grid">
    <div class="card"><div class="card-icon">⚙️</div><div class="card-title">CenterServer</div><div class="card-desc">节点注册中心，不执行业务 RPC，只负责节点发现与广播。NodeManager 维护 datasByNodeId，3秒上报间隔，6秒无上报视为失效</div></div>
    <div class="card"><div class="card-icon">🖥️</div><div class="card-title">RpcNode</div><div class="card-desc">每个服务进程的 RPC 节点，包含一个 RpcServer(BaseServer) + 若干 BaseClient(connectToOthers) + 一个 ReportClient。通过 rpc_server_system 表保证 serverId 唯一</div></div>
    <div class="card"><div class="card-icon">📝</div><div class="card-title">@RpcService / @RpcMethod</div><div class="card-desc">标记 RPC 服务类（继承于 BaseService）和 RPC 方法。使用CallUtils.init() 注册本进程提供的 RPC 方法，通过gen模块将服务名生成到 CallEnum</div></div>
    <div class="card"><div class="card-icon">📞</div><div class="card-title">RpcFunction</div><div class="card-desc">调用侧统一入口。维护 callIdNodes（方法ID→注册了该方法的节点列表），屏蔽节点选择、本地/远程调用、回调注册、超时控制</div></div>
</div>

<h2>节点启动与发现</h2>
<h3>1. RpcNode 启动流程</h3>
<ol class="step-list">
    <li>查询 <code>rpc_server_system</code> 表，确保 serverId 唯一（已有则复用端口，否则新增）</li>
    <li>自动分配或复用端口（查询表中已有记录）</li>
    <li>启动本地 RPC Server（BaseServer）</li>
    <li>创建 ReportClient 连接 CenterServer（连接失败 5 秒后自动重连）</li>
    <li>连接成功后全量上报自身信息（ip/port/serverId/nodeId/nodeType）</li>
</ol>
<pre><code class="language-java">// RpcNode.start() 核心逻辑
public void start() {
    // 1. 查询 rpc_server_system 表
    Map&lt;String, Object&gt; serverInfo = dbService.queryGetOneByParams(
        "rpc_server_system", "server_id", serverId);
    if (serverInfo == null) {
        // 新节点：插入记录，自动分配端口
        port = dbService.executeWithGeneratedKey(
            "INSERT INTO rpc_server_system (ip, port, status) VALUES (?, ?, 1)", ip, port);
    } else {
        // 旧节点：复用端口
        port = (int) serverInfo.get("port");
    }
    // 2. 启动 RPC Server
    rpcServer = new BaseServer(new RpcServerMessageManager());
    rpcServer.startListen(ip, port);
    // 3. 连接中心服
    connectMaster();
}</code></pre>

<h3>2. 中心服广播机制</h3>
<p>CenterServer 收到节点上报后，由 <code>NodeManager.updateNode()</code> 处理：</p>
<ul>
    <li>如果是新节点 → 调用 <code>broadcastToNode()</code> 双向广播：把旧节点信息同步给新节点，把新节点信息同步给所有旧节点</li>
    <li>如果是旧节点（心跳上报）→ 更新 <code>reportTime</code>，不做广播</li>
    <li>节点失效检测：<code>isNodeDead()</code> 判断超过 2 倍上报间隔（6秒）无上报则视为失效，从列表移除</li>
</ul>

<h3>3. 节点互连</h3>
<p>各节点收到中心服广播的其他节点地址后：</p>
<ol class="step-list">
    <li><code>ReportClientMessageManager.pulseHandlerOne()</code> 收到中心服消息</li>
    <li>调用 <code>RpcNode.connectOther(message)</code> 解析 ip/port/serverId</li>
    <li>创建 BaseClient 连接对方 RPC Server</li>
    <li>握手完成后，<code>RpcServerHandler.onRecvConnect()</code> 推送自身支持的 RPC 方法 ID 列表</li>
    <li>对方收到后调用 <code>RpcFunction.update()</code> 更新路由表</li>
</ol>

<h3>4. RPC 连接策略（RpcConnectPolicy）</h3>
<p>多进程部署时，默认各节点会尽量全量互连。若希望减少不必要的 TCP 连接（例如 HTTP 服只需被访问、不必主动连所有 Game），可在<strong>中心服配置</strong> <code>center-config.properties</code> 中声明出站连接规则。中心服在 <code>NodeManager.broadcastToNode()</code> 时按策略过滤：仅当 <code>shouldConnect(接收方类型, 目标类型)</code> 为 true 时才把目标地址广播给接收方。</p>
<div class="callout callout-info">
    <p>未配置任何 <code>rpc.connect.*</code> 项时，策略关闭，保持全量互连。</p>
</div>
<p>配置格式：</p>
<pre><code class="language-properties"># rpc.connect.&lt;本节点类型&gt;=&lt;允许主动连接的目标类型&gt;，逗号分隔
rpc.connect.external=game,http,gmback
rpc.connect.game=external,global,gmback
rpc.connect.global=game
rpc.connect.gmback=game,http
rpc.connect.http=</code></pre>
<table>
<thead><tr><th>nodeType</th><th>对应进程</th><th>配置中的 serverId 示例</th></tr></thead>
<tbody>
<tr><td>external</td><td>ExternalServer</td><td>100</td></tr>
<tr><td>game</td><td>GameServer / RunAllOne</td><td>200</td></tr>
<tr><td>http</td><td>HttpServer</td><td>3</td></tr>
<tr><td>gmback</td><td>GmBackServer</td><td>2</td></tr>
<tr><td>global</td><td>GlobalServer</td><td>4000</td></tr>
</tbody>
</table>
<p>各 RPC 进程须在自身配置中设置 <code>rpc.node.type</code>（与上表一致）和 <code>rpc.node.serverId</code>（集群内唯一整数）。自定义 RPC 服务也需使用未占用的 type 名，并在中心服策略中补充对应规则。</p>

<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">RpcNode A</span>
        <span class="flow-arrow">→ ReportClient 上报 →</span>
        <span class="flow-node flow-node-warning">CenterServer</span>
        <span class="flow-arrow">→ 广播 A 的地址 →</span>
        <span class="flow-node flow-node-secondary">RpcNode B</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">RpcNode B</span>
        <span class="flow-arrow">→ 创建 BaseClient 连接 A →</span>
        <span class="flow-node flow-node-secondary">RpcNode A</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">A</span>
        <span class="flow-arrow">→ 握手后推送 callId 列表 →</span>
        <span class="flow-node flow-node-secondary">B</span>
        <span class="flow-arrow">→ 握手后推送 callId 列表 →</span>
        <span class="flow-node flow-node-secondary">A</span>
    </div>
</div>

<h2>方法注册机制</h2>
<h3>CallEnum</h3>
<p>每个 RPC 方法对应一个 int 编号，定义在 <code>CallEnum.java</code> 中，在gen模块下genRpc包下新增服务接口，运行<code>GenRpcStartUp</code>自动生成</p>
<pre><code class="language-java">public class CallEnum {
    public static final int ChatRpcListenService_onChat = 1;
    public static final int GlobalChatService_chat = 2;
    public static final int GlobalChatService_history = 3;
    public static final int ExternalRecvGameMessageService_recvMessage = 4;
    public static final int FriendRpcListenService_onNewFriendRequest = 5;
    public static final int FriendRpcListenService_onFriendAdded = 6;
    public static final int FriendRpcListenService_onFriendDeleted = 7;
    public static final int GlobalFriendService_handleFriendRequest = 8;
    // ... 共 30 个方法
}</code></pre>

<h3>CallUtils.init() 完整流程</h3>
<ol class="step-list">
    <li>扫描指定包路径下所有 <code>@RpcService</code> 类</li>
    <li>实例化服务类（构造参数为 nodeId）</li>
    <li>注册到 <code>ServiceManager</code>（类名 → 实例映射）</li>
    <li>扫描所有 <code>@RpcMethod</code> 方法</li>
    <li>建立 <code>rpcId → Method</code> 映射（rpcIdToMethodMap）</li>
    <li>把当前节点支持的 callId 写入 <code>RpcFunction.callIdNodes</code></li>
    <li>调用 <code>ServiceManager.initAll()</code> 启动所有服务生命周期</li>
</ol>

<h2>RPC 调用模式</h2>
<h3>SendRandom（随机调用）</h3>
<p>默认模式。如果当前节点自己注册了此方法，优先本地处理（不经过网络）；否则从远端节点随机选一个。</p>
<pre><code class="language-java">// 示例：发送聊天消息（随机选一个 Global 节点处理）
RpcFunction.newInstance().call(CallEnum.GlobalChatService_chat,
    humanId, name, message);</code></pre>

<h3>SendAll（广播调用）</h3>
<p>给所有注册了该方法的节点都发一份。</p>
<pre><code class="language-java">// 示例：通知所有 Game 节点有新好友申请
RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll).call(
    CallEnum.FriendRpcListenService_onNewFriendRequest,
    targetHumanId);</code></pre>

<h3>SendDesignated（定向调用）</h3>
<p>按指定 serverNodeId 发给某一个目标节点。如果目标节点无效，退化为 SendRandom。External ↔ Game 之间的消息转发大量使用此模式。</p>
<pre><code class="language-java">// 示例：Game 向指定 External 节点发送消息
RpcFunction.newInstance(externalNodeId).call(
    CallEnum.ExternalRecvGameMessageService_recvMessage,
    connectionId, data,
    firstSend ? "" : RpcNodeManager.getRpcServerNodeId());  // 首次发送带 gameNodeId</code></pre>

<h3>调用参数说明</h3>


<p>以邮件系统举例，发送邮件时调用写法是：</p>
<pre><code class="language-java">RpcFunction.newInstance().call(
    CallEnum.GlobalMailService_sendMail,
    humanId, templateId, attachmentsJson, senderName
);</code></pre>
<p>参数按目标方法签名<strong>顺序</strong>直接传入，底层会放入 <code>Object[] data</code> 数组，经 MessagePack 序列化后发送。</p>

<p>底层服务收到 call 调用时，会调用 <code>CallUtils.handler()</code> 处理此 call，此方法内部调用：</p>
<pre><code class="language-java">// 将call中的数据，传递给方法的参数
private static Object[] parseCallArgs(Call call, Method method) {
    Object[] args = new Object[method.getParameterCount()];
    for (int i = 0; i < args.length; i++) {
        args[i] = call.getData(i);
    }
    return args;
}
</code></pre>
<p>通过 <code>call.getData(i)</code> 按位置取出数据，作为参数传递给服务方法。调用侧只需保证<strong>参数个数与顺序</strong>与 <code>@RpcMethod</code> 方法签名一致（由 <code>CallEnum</code> 绑定到具体 Method）。</p>
<p><strong>注意</strong>：<code>returns()</code> 与 <code>listenResult()</code> 的 context 仍使用键值对；仅 <code>call()</code> 请求参数改为按位置传值。</p>

<pre><code class="language-java">@RpcService
public class GlobalMailService extends BaseService {
    @RpcMethod
    public void sendMail(String humanId, int templateId, String attachmentsJson, String senderName) {}    
}
</code></pre>

<h2>回调与超时</h2>

<h3>服务方法返回数据</h3>
<p>以聊天服务举例，获取历史记录时，调用returns()，返回了两个键值对</p>
<pre><code class="language-java">@RpcMethod
    public void history(String humanId) {
        ChatProto.MS2C_History.Builder historyBuilder = ChatProto.MS2C_History.newBuilder();
        ...
        byte[] data = historyBuilder.build().toByteArray();
        returns("humanId", humanId, "info", data);
    }</code></pre>
<h3>调用方注册回调</h3>
<p>可以看到<code>rpcFunction.listenResult(Callback<RpcResult> callback, Object... contexts)</code>第一个参数为回调函数，第二个参数为上下文（非必须，但处理玩家数据时一定要把玩家id做为上下文传递，
回调里通过上下文获取玩家id，再根据id获取humanObject， 避免Lambda 持有 this引用，导致内存泄露问题）。
那么在回调执行时，可以通过<code>rpcResult.getContext("humanId")</code>取出传递的数据。通过<code>rpcResult.getResult()</code>判断本次回调的错误码，通过<code>rpcResult.getData()</code>获取远端返回的数据。
注意调用<code>rpcResult.getData()</code>方法传递的参数名一定要和远端返回时所用的参数名保持一致。</p>
<pre><code class="language-java">@MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_GetHistory_VALUE)
    public static void history(HumanObject humanObject) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalChatService_history, humanObject.getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            String humanId = (String) rpcResult.getContext("humanId");
            HumanObject humanObj = HumanObjectManager.getHumanObject(humanId);
            if (humanObj == null) return;
            if (rpcResult.getResult() != ErrorType.SUCCESS) return;
            byte[] protoData = (byte[]) rpcResult.getData("info");
            humanObj.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_History_VALUE, protoData);
        }, "humanId", humanObject.getHumanId());
    }</code></pre>

<h3>超时机制</h3>
<ul>
    <li>默认超时：<strong>10 秒</strong>（RpcManager.registerCallback 设置）</li>
    <li>自定义超时：<code>rpcFunction.setTimeOut(millis)</code></li>
    <li>超时检测：<code>RpcServerMessageManager.pulseListenRpcTimeout()</code> 持续扫描 checkTimeout Map</li>
    <li>超时后：移除回调，构造 <code>RPC_TIMEOUT</code> 错误码，触发回调</li>
</ul>

<h2>Call 消息结构</h2>
<p>Call 继承 BaseMessage，是 RPC 通信的核心消息对象：</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>nodeId</td><td>String</td><td>发送方节点 ID</td></tr>
<tr><td>toNodeId</td><td>String</td><td>目标节点 ID</td></tr>
<tr><td>messageId</td><td>long</td><td>唯一消息 ID（用于回调匹配）</td></tr>
<tr><td>rpcId</td><td>int</td><td>RPC 方法编号（对应 CallEnum）</td></tr>
<tr><td>type</td><td>CallType</td><td>Call（业务调用）或 Update（方法列表更新）</td></tr>
<tr><td>data</td><td>Object[]</td><td>请求参数数组（按位置对应方法参数）</td></tr>
<tr><td>result</td><td>int</td><td>调用结果码（0=成功，100+=错误）</td></tr>
</tbody>
</table>

<h2>错误码</h2>
<table>
<thead><tr><th>错误码</th><th>常量名</th><th>说明</th><th>触发场景</th></tr></thead>
<tbody>
<tr><td>0</td><td>SUCCESS</td><td>成功</td><td>正常返回</td></tr>
<tr><td>100</td><td>RPC_TIMEOUT</td><td>RPC 调用超时</td><td>10秒内未收到回包</td></tr>
<tr><td>101</td><td>RPC_SERVICE_NOT_FOUND</td><td>服务未找到</td><td>CallEnum 对应的 @RpcService 不存在</td></tr>
<tr><td>102</td><td>RPC_METHOD_NOT_FOUND</td><td>方法未找到</td><td>CallEnum 对应的 @RpcMethod 不存在</td></tr>
<tr><td>103</td><td>RPC_ARGS_NOT_MATCH</td><td>参数数量不匹配</td><td>call() 传的参数个数 ≠ 方法参数个数</td></tr>
<tr><td>104</td><td>RPC_CALL_CATCH</td><td>调用异常</td><td>方法执行抛出异常</td></tr>
<tr><td>105</td><td>RPC_NOT_REGISTER</td><td>方法未注册</td><td>callIdNodes 中没有节点注册了该方法</td></tr>
</tbody>
</table>

<h2>CallContext 调用上下文栈</h2>
<p>使用 LinkedList 实现栈结构，用于在 <code>returns()</code> 时获取当前正在处理的 Call：</p>
<pre><code class="language-java">// CallUtils.handler() 中：
CallContext.push(call);  // 压栈
method.invoke(service, args);  // 执行业务方法
// 业务方法内部调用 returns() 时：
CallUtils.returns(nodeId, result, params);
// returns 内部：
Call currentCall = CallContext.getLastCall();  // 取栈顶</code></pre>

<h2>关键约束</h2>
<div class="callout callout-danger">
    <p><strong>⚠️ 一个进程只能有一个 RpcNode</strong>：RpcNodeManager 维护单例静态引用，一个 JVM 进程只能创建一个 RpcNode。</p>
</div>
<div class="callout callout-danger">
    <p><strong>⚠️ 参数数量必须严格匹配</strong>：<code>call()</code> 传的参数个数必须和方法参数个数一致，且顺序要和目标方法参数顺序一致。CallUtils.handler 会校验 data.length == method.getParameterCount()。</p>
</div>

<h2>ServiceManager 生命周期</h2>
<p>每个 <code>@RpcService</code> 实例都交给 ServiceManager 托管：</p>
<table>
<thead><tr><th>方法</th><th>频率</th><th>说明</th></tr></thead>
<tbody>
<tr><td>initAll()</td><td>启动时一次</td><td>执行 service.init()，异步从 server_data 表加载数据，等待初始化完成，注册 shutdown hook 做同步保存</td></tr>
<tr><td>pulse()</td><td>每帧（5ms）</td><td>驱动所有服务的 pulse()</td></tr>
<tr><td>pulsePerSec()</td><td>每秒</td><td>驱动所有服务的 pulsePerSec()</td></tr>
<tr><td>pulsePer5Sec()</td><td>每 5 秒</td><td>驱动所有服务的 pulsePer5Sec()</td></tr>
<tr><td>pulsePerMin()</td><td>每分钟</td><td>驱动所有服务的 pulsePerMin() + save()（异步保存到 server_data 表）</td></tr>
<tr><td>save()</td><td>按需/每分钟</td><td>异步保存所有服务数据到 server_data 表</td></tr>
<tr><td>saveSync()</td><td>关闭时</td><td>同步保存（shutdown hook 调用）</td></tr>
</tbody>
</table>

<h2>消息管理器</h2>
一个RPC节点包含两个消息管理器：<code>RpcServerMessageManager</code>和<code>RpcClientMessageManager</code>，消息管理器继承于基类<code>BaseMessageManager</code><a href="#/network"> (详见网络层)</a>
<h3>RPC服务器节点消息管理器</h3>
<p>职责是：执行当前节点所有服务的心跳、心跳检测rpc调用超时、处理远端节点发来的rpc调用、处理发起rpc调用后的回调、更新本地保存的其他节点拥有的rpc方法列表。所有业务都在同一消息管理器中单线程处理。</p>
<pre><code class="language-java">public class RpcServerMessageManager extends ServerMessageManager {
    @Override
    public void pulse() {
        pulseHandler();
        pulseSender();
        pulseListenRpcTimeout();
        ServiceManager.pulse();
    }
    @Override
    protected void pulseHandlerOne(Object data) {
        ...
        if (message.getType() == CallType.Call.ordinal()) {
            CallUtils.handler(message);
        } else if (message.getType() == CallType.CallResult.ordinal()) {
            RpcManager.callResult(message);
        } else if (message.getType() == CallType.Update.ordinal()) {
            RpcFunction.onUpdate(message);
        }
    }
}</code></pre>

<h3>RPC客户端节点消息管理器</h3>
<p>职责是：发起rpc调用后，都会通过此管理器最终发给远端节点。不论RPC节点连接了多少个远端节点，当前节点的所有客户端都使用同一个消息管理器，单线程处理call调用的发送。</p>
<pre><code class="language-java">public class RpcClientMessageManager extends ClientMessageManager {
    @Override
    public void pulse() {
        pulseSender();
    }
}</code></pre>

<h2>BaseService 数据操作</h2>
<pre><code class="language-java">@RpcService
public class GlobalChatService extends BaseService {
    public GlobalChatService(String nodeId) { super(nodeId); }

    @RpcMethod
    public void chat(String humanId, String message) {
        // 处理业务...
        // 回包给调用方
        returns("success", true, "data", resultData);
    }

    // 数据持久化（存到 server_data 表）
    @Override
    public void save() {
        putDbData("chatHistory", chatHistoryList);
    }

    @Override
    public void load() {
        getDbData("chatHistory", new TypeReference&lt;List&lt;ChatRecord&gt;&gt;(){}, value -> {
            if (value != null) chatHistoryList = value;
        });
    }
}</code></pre>
`);
