registerPage('architecture', '架构总览', '多进程架构、服务职责、数据流、单线程设计', () => `
<h1>架构总览</h1>
<p class="page-desc">Sunrise Game Frame 采用"中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台"的多进程架构，所有业务节点支持动态扩容</p>

<h2>架构图</h2>
<div class="arch-diagram">
    <img src="https://files.seeusercontent.com/2026/04/27/Im3e/sunrise-game-frame.png" alt="架构图" style="max-width:100%;border-radius:8px;" />
</div>

<h2>各服务职责</h2>
<div class="card-grid">
    <div class="card">
        <div class="card-icon">⚙️</div>
        <div class="card-title">CenterServer</div>
        <div class="card-desc">所有 RPC 节点的注册中心。接收节点上报（ip/port/serverId/nodeId）、维护在线节点信息、新节点加入时双向广播、让各节点互连。不执行业务 RPC，只做"节点通讯录同步"。6秒无上报视为失效</div>
    </div>
    <div class="card">
        <div class="card-icon">🌐</div>
        <div class="card-title">ExternalServer</div>
        <div class="card-desc">对外网关服。同时监听 TCP/WS/KCP 三种协议（端口依次 +1）、验证客户端首包认证、分配 connectionId、消息频率限制（300条/分钟，64KB上限）、转发消息到 Game 服、接收 Game 回包发回客户端</div>
    </div>
    <div class="card">
        <div class="card-icon">🎮</div>
        <div class="card-title">GameServer</div>
        <div class="card-desc">核心业务服。登录流程（创建/加载账号、角色）、玩家对象管理（HumanObject）、模块生命周期（init/load/save/sendToClient）、协议路由（@MsgHandlerClass、@MsgHandlerMethod）、定时存档、调用 Global 跨服服务，处理gm后台指令</div>
    </div>
    <div class="card">
        <div class="card-icon">🌍</div>
        <div class="card-title">GlobalServer</div>
        <div class="card-desc">全局跨服服务。所有需要跨服共享的数据和逻辑都放在这里，目前实现了：聊天服务（GlobalChatService）、好友服务（GlobalFriendService）、邮件服务（GlobalMailService）、玩家简要信息查询（GlobalPlayerInfoService）</div>
    </div>
    <div class="card">
        <div class="card-icon">🔗</div>
        <div class="card-title">HttpServer</div>
        <div class="card-desc">HTTP 服务（基于 Javalin）。为客户端分配对外服地址（同一 uid 优先分配之前的 external）、接收对外服心跳上报、维护地址池、提供服务器开关和白名单接口</div>
    </div>
    <div class="card">
        <div class="card-icon">🛡️</div>
        <div class="card-title">GmBackServer</div>
        <div class="card-desc">GM 后台（基于 Javalin + JWT）。登录认证、节点监控、配置热更、发邮件、踢人、封禁/禁言名单广播、用户与操作日志管理。前端使用 Vue 3 + Element Plus</div>
    </div>
</div>

<h2>核心数据流</h2>
<h3>客户端消息上行（Client → Game）</h3>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ Http请求对外服状态与地址 →</span>
        <span class="flow-node flow-node-warning">HttpServer</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ TCP/WS/KCP →</span>
        <span class="flow-node flow-node-success">ExternalServer</span>
        <span class="flow-arrow">→ 首次带 externalNodeId →</span>
        <span class="flow-node flow-node-secondary">GameServer</span>
        <span class="flow-arrow">→ 解析 Protobuf →</span>
        <span class="flow-node flow-node-secondary">GameMasterService</span>
        <span class="flow-arrow">→ 消息处理 →</span>
        <span class="flow-node flow-node-secondary"> LogicUtils.handler()</span>
    </div>
</div>
<p>消息经过 External 转发时，External 会在首次转发时带上自己的 <code>externalNodeId</code>，Game 收到<code>externalNodeId</code>，则记录此玩家当前在这个对外服，后续回包直接通过rpc调用定向发送到该 External 节点。</p>

<h3>服务器消息下行（Game → Client）</h3>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">Module/Handler</span>
        <span class="flow-arrow">→ ConnectObject.sendMsg() →</span>
        <span class="flow-node flow-node-secondary">GameServer</span>
        <span class="flow-arrow">→ RPC SendDesignated →</span>
        <span class="flow-node flow-node-success">ExternalServer</span>
        <span class="flow-arrow">→ Channel.writeAndFlush →</span>
        <span class="flow-node flow-node-primary">Client</span>
    </div>
</div>
<p>Game 服通过 <code>ConnectObject.sendMsg()</code> 发送消息，内部调用 <code>RpcFunction.newInstance(externalNodeId).call(CallEnum.ExternalRecvGameMessageService_recvMessage, ...)</code>，则会将此玩家的消息，发给他所在的对外服节点，首次发送还会带上游戏服自己的节点ID <code>gameNodeId</code> ，对外服就知道此玩家在哪个游戏服了，完成双向绑定。</p>

<h3>跨服调用流程（Game → Global → Game）</h3>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-arrow">→ RpcFunction.call(SendRandom) →</span>
        <span class="flow-node flow-node-warning">GlobalServer</span>
        <span class="flow-arrow">→ 处理业务 →</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-warning">GlobalServer</span>
        <span class="flow-arrow">→ RpcFunction.call(SendAll) →</span>
        <span class="flow-node flow-node-secondary">Game A/B/C</span>
        <span class="flow-arrow">→ 群发给所有玩家 → ConnectObject.sendMsg() → </span>
        <span class="flow-node flow-node-primary">Client</span>
    </div>
</div>
<p>典型场景：玩家发聊天 → Game 调用 <code>GlobalChatService_chat</code>(SendRandom) → Global 处理后广播 <code>GameRpcListenService_sendToAllHuman</code>(SendAll) → 所有 Game 节点各自推送给本服的在线玩家。</p>

<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-arrow">→ RpcFunction.call(SendRandom)、listenResult() →</span>
        <span class="flow-node flow-node-warning">GlobalServer</span>
        <span class="flow-arrow">→ 处理业务 →</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-warning">GlobalServer</span>
        <span class="flow-arrow">→ returns("param1", ..., "param2", ...); →</span>
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-arrow">→ 执行RPC回调 → ConnectObject.sendMsg() → </span>
        <span class="flow-node flow-node-primary">Client</span>
    </div>
</div>
<p>典型场景：玩家获取聊天记录 → Game 调用 <code>GlobalChatService_history</code>(SendRandom) → Global 处理后组织数据返回 <code>returns("humanId", humanId, "info", data)</code>返回给调用此方法的 Game 节点，Game 节点收到后发给客户端。</p>

<h3>GM 指令广播流程</h3>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-danger">GM 后台</span>
        <span class="flow-arrow">→ REST API →</span>
        <span class="flow-node flow-node-danger">GmBackServer</span>
        <span class="flow-arrow">→ RpcFunction.call(SendAll) →</span>
        <span class="flow-node flow-node-secondary">Game A/B/C</span>
    </div>
</div>
<p>GM 后台通过 <code>GameRecvGmBackMessageService_recvMessage</code> 广播指令（reloadConfig/kickHuman/banHumanList/muteHumanList），每个 Game 节点根据本地在线玩家情况执行对应动作。</p>

<h2>单线程无锁设计</h2>
<p>本项目采用 <strong>单线程 DispatchThread + 消息队列</strong> 模型：</p>
<ul>
    <li>每个服务进程内的消息管理器运行独立的 DispatchThread（5ms 间隔心跳）</li>
    <li>所有业务逻辑在单线程内顺序执行，无需加锁</li>
    <li>DB 异步操作通过 <code>AsyncEventManager</code> 将回调投递回主线程</li>
    <li>RPC 调用通过 <code>messageId</code> 实现异步回调，不阻塞主线程</li>
    <li>玩家消息队列<code>HumanObject.msgQueue</code>客户端发来的消息包，在队列中单线程处理</li>
</ul>
<div class="callout callout-tip">
    <p><strong>💡 设计理念</strong>：游戏服务器中玩家操作天然串行，单线程模型避免了多线程并发问题（无需 synchronized / volatile / ConcurrentHashMap），同时通过异步 IO 保证吞吐量。</p>
</div>

<h2>BaseMessageManager 消息驱动核心</h2>
<p>所有服务进程的消息管理器都继承自 <code>BaseMessageManager</code>，它是整个框架的消息驱动核心：</p>
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

<h2>端口分配</h2>
<table>
<thead><tr><th>服务</th><th>端口</th><th>协议</th><th>用途</th></tr></thead>
<tbody>
<tr><td>CenterServer</td><td>8000</td><td>TCP</td><td>RPC 节点注册中心（内部通信）</td></tr>
<tr><td>ExternalServer</td><td>10000</td><td>TCP</td><td>客户端 TCP 连接</td></tr>
<tr><td>ExternalServer</td><td>10001</td><td>WebSocket</td><td>客户端 WS 连接</td></tr>
<tr><td>ExternalServer</td><td>10002</td><td>KCP</td><td>客户端 KCP 连接</td></tr>
<tr><td>HttpServer</td><td>8090</td><td>HTTP</td><td>地址分发接口</td></tr>
<tr><td>GmBackServer</td><td>8010</td><td>HTTP</td><td>GM 后台管理</td></tr>
<tr><td>MySQL</td><td>3306</td><td>TCP</td><td>数据库</td></tr>
<tr><td>RPC服务</td><td>20000+ 自动分配</td><td>TCP</td><td>RPC 服务端口，所有RPC节点都会占用一个端口</td></tr>
</tbody>
</table>
`);
