registerPage('architecture', '架构总览', '多进程架构、服务职责、数据流、单线程设计', () => `
<h1>架构总览</h1>
<p class="page-desc">Sunrise Game Frame 采用"中心服 + 对外服 + 游戏服 + 全局服 + HTTP 服务 + GM 后台 + 可扩展 RPC 服务"的架构，可多进程拆分，也可组合为单进程。RPC 链接可动态发现，但有状态业务仍需遵守所有权约束。</p>

<h2>架构图</h2>
<div class="arch-diagram">
    <img src="img/sunrise-game-frame.png" alt="架构图" style="max-width:100%;border-radius:8px;" />
</div>

<h2>各服务职责</h2>
<div class="card-grid">
    <div class="card">
        <div class="card-icon">⚙️</div>
        <div class="card-title">CenterServer</div>
        <div class="card-desc">RPC 节点的发现中心。接收 ip/port/serverId/nodeId/nodeType/instanceId 上报，按 rpc.connect.* 决定向谁广播对端地址（未配置则全量互连），3 秒心跳，6 秒无上报视为失效。内置 Dashboard（默认 8088）展示拓扑并提供 Center 热更 HTTP 入口。不执行业务 RPC。</div>
    </div>
    <div class="card">
        <div class="card-icon">🌐</div>
        <div class="card-title">ExternalServer</div>
        <div class="card-desc">客户端网关。TCP/WS/KCP 可通过配置独立启停，启用时占用连续端口块（base、base+1、base+2）。校验首包前缀、分配 connectionId、执行每连接令牌桶限流，把业务包转发到 Game 节点并把回包写回客户端。</div>
    </div>
    <div class="card">
        <div class="card-icon">🎮</div>
        <div class="card-title">GameServer</div>
        <div class="card-desc">核心业务服。登录流程（创建/加载账号、角色）、玩家对象管理（HumanObject）、模块生命周期（init/load/save/sendToClient）、协议路由（@MsgHandlerClass、@MsgHandlerMethod）、定时存档、调用 Global 跨服服务，处理gm后台指令</div>
    </div>
    <div class="card">
        <div class="card-icon">🌍</div>
        <div class="card-title">GlobalServer</div>
        <div class="card-desc">跨逻辑服共享服务，目前包含聊天、好友、邮件、排行榜与玩家简要信息。状态当前保存在进程内存并周期性写入 server_data；多实例部署时，必须按玩家或状态键分片，确保每份状态只有一个写入实例，不能由多个实例随机读写。</div>
    </div>
    <div class="card">
        <div class="card-icon">🔗</div>
        <div class="card-title">HttpServer</div>
        <div class="card-desc">HTTP 服务（基于 Javalin）。为客户端分配对外服地址、提供邮箱注册/登录（验证码 + JWT）、服务器开关/白名单/公告；开启 <code>player.auth.enabled</code> 后地址类接口需携带 Token</div>
    </div>
    <div class="card">
        <div class="card-icon">🛡️</div>
        <div class="card-title">GmBackServer</div>
        <div class="card-desc">GM 后台 API（Javalin + JWT，端口 admin.port）。提供节点监控、运营指令、金手指、配置重载、代码热更、用户权限与操作日志。浏览器页面由前端工程 gmback-ui（Vue 3 SPA）提供，API 与页面分进程部署。</div>
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
        <span class="flow-node flow-node-danger">gmback-ui</span>
        <span class="flow-arrow">→ /api →</span>
        <span class="flow-node flow-node-danger">GmBackServer</span>
        <span class="flow-arrow">→ RpcFunction.call(SendAll) →</span>
        <span class="flow-node flow-node-secondary">Game A/B/C</span>
    </div>
</div>
<p>GM 后台通过 <code>GameRecvGmBackMessageService_recvMessage</code> 下发 Game 运营指令（reloadConfig/kickHuman/banHumanList/muteHumanList/cdkList），每个 Game 节点根据本地在线玩家情况执行对应动作。代码热更使用所有 RPC 服务共同注册的 <code>HotswapControlService_command</code>，由 GM 逐个节点调用并等待真实结果。兑换码兑换成功后 Game 反向上报 <code>cdkRedeem</code> 至 GmBack。</p>

<h2>单线程无锁设计</h2>
<p>每个 RPC 节点采用 <strong>单业务 DispatchThread + 消息队列</strong> 模型：</p>
<ul>
    <li>RPC Server 的消息管理器以 5ms 间隔执行接收、发送、RPC 超时检测和 ServiceManager 心跳</li>
    <li>客户端协议、RPC 回调、玩家消息队列和 GameSystem 在同一业务线程上串行推进，玩家内存状态无需业务锁</li>
    <li>Netty EventLoop、Javalin/Jetty 和数据库线程池是独立线程；DB 回调通过 <code>AsyncEventManager</code> 再投递回游戏业务线程</li>
    <li>RPC 调用通过 <code>messageId</code> 实现异步回调，不阻塞主线程</li>
    <li>玩家消息队列<code>HumanObject.msgQueue</code>客户端发来的消息包，在队列中单线程处理</li>
</ul>
<div class="callout callout-tip">
    <p><strong>💡 线程归属</strong>：业务串行并不等于整个 JVM 单线程。编写 HTTP、DB 回调、Controller 或热更逻辑时仍必须明确线程归属；跨线程进入游戏状态应通过消息或异步事件回到业务 DispatchThread。</p>
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
<tr><td>HttpServer</td><td>8090</td><td>HTTP</td><td>邮箱注册登录 + 地址分发接口</td></tr>
<tr><td>GmBackServer</td><td>8010</td><td>HTTP</td><td>GM 后台 REST API（页面见 gmback-ui）</td></tr>
<tr><td>MySQL</td><td>3306</td><td>TCP</td><td>数据库</td></tr>
<tr><td>RPC服务</td><td>20000+ 自动分配</td><td>TCP</td><td>RPC 服务端口，所有RPC节点都会占用一个端口</td></tr>
</tbody>
</table>
<p>端口均可通过对应配置文件调整。External 的 TCP/WS/KCP 可以按需关闭，关闭的协议端口上报为 0；RPC 端口保存在 <code>rpc_server_system</code>，相同 serverId 重启时优先复用原端口。</p>
`);
