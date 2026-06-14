registerPage('game-server', '游戏服', '玩家对象、模块系统、消息路由、登录流程', () => `
<h1>游戏服</h1>
<p class="page-desc">GameServer 是核心业务服，承载玩家会话与核心玩法的执行</p>

<h2>核心职责</h2>
<ul>
    <li>登录流程（创建/加载账号、角色选择）</li>
    <li>加载/创建玩家对象（HumanObject）</li>
    <li>玩家消息队列处理（msgQueue + isCalling RPC 锁）</li>
    <li>玩家模块生命周期管理（init/load/save/sendToClient）</li>
    <li>定时保存玩家数据（每分钟）</li>
    <li>调用 Global 服完成跨服业务</li>
    <li>响应 GM 后台命令（reloadConfig/hotswapJar/kick/ban/mute/cdkList）</li>
    <li>兑换码校验与发奖（CdkSystem + CdkModule + ChatMsgHandler）</li>
</ul>

<h2>GameServerStartUp 启动流程</h2>
<pre><code class="language-java">public class GameServerStartUp {
    public static void main(String[] args) {
        ConfigReader.loadConfig(args[0]); // 默认 game-config.properties
        Properties properties = ConfigReader.getProp();
        int serverId = Integer.parseInt(properties.getProperty("rpc.node.server-id"));
        String nodeType = properties.getProperty("rpc.node.type");
        RpcNode rpcNode = RpcNodeManager.createRpcNode(serverId, nodeType);
        ConfigUtils.load();           // 加载 Luban 配置表
        ProtoParserUtils.init();      // 注册协议解析器（TOPIC → parseFrom）
        LogicUtils.init("org.sunrise.game.game.logic");  // 扫描 @MsgHandler
        ModuleUtils.init("org.sunrise.game.game.modules"); // 扫描 @HumanModule
        GameSystemUtils.init("org.sunrise.game.game.logic.system"); // 扫描 @GameSystem
        CallUtils.init(rpcNode.getNodeId(),
            Collections.singletonList("org.sunrise.game.game.service"),
            CallEnum.class);
        rpcNode.start();
    }
}</code></pre>

<h2>玩家对象</h2>
<h3>HumanObject</h3>
<p>一个在线玩家实例，包含：</p>
<table>
<thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
<tbody>
<tr><td>connectObject</td><td>ConnectObject</td><td>连接上下文，负责把消息发回 External</td></tr>
<tr><td>humanId</td><td>String</td><td>角色唯一 ID（雪花算法生成）</td></tr>
<tr><td>serverId</td><td>int</td><td>所在服务器 ID</td></tr>
<tr><td>roleData</td><td>Map&lt;String,String&gt;</td><td>角色存档数据（模块名 → JSON 字符串）</td></tr>
<tr><td>modules</td><td>Map&lt;String,BaseModule&gt;</td><td>模块 Map（类名 → 模块实例）</td></tr>
<tr><td>msgQueue</td><td>Queue</td><td>消息队列，等待处理的消息</td></tr>
<tr><td>isCalling</td><td>boolean</td><td>RPC 锁，RPC 返回前消息排队</td></tr>
<tr><td>lastPingTime</td><td>long</td><td>最后一次 Ping 时间</td></tr>
<tr><td>lastSaveDbTime</td><td>long</td><td>最后一次存库时间</td></tr>
</tbody>
</table>

<h3>ConnectObject</h3>
<p>连接对象，封装 connectId、uid、accountId、externalNodeId。通过 RPC 调用 ExternalRecvGameMessageService_recvMessage 发送消息给客户端。</p>
<pre><code class="language-java">public class ConnectObject {
    private String connectId;
    private String externalId;
    private String uid;
    private int accountId;
    private boolean firstSend;       // 是否首次发送（首次带 gameNodeId）
    private String externalNodeId;   // 绑定的 External 节点 ID

    public void sendMsg(int packetType, int packetId, ByteString data) {
        RpcFunction.newInstance(externalNodeId).call(
            CallEnum.ExternalRecvGameMessageService_recvMessage,
            connectId, bytes,
            firstSend ? "" : RpcNodeManager.getRpcServerNodeId()  // 首次发送带 gameNodeId
        );
    }
}</code></pre>

<h3>HumanObjectManager</h3>
<p>玩家对象管理器，维护多种映射：</p>
<table>
<thead><tr><th>集合</th><th>映射</th><th>说明</th></tr></thead>
<tbody>
<tr><td>humanObjects</td><td>humanId → HumanObject</td><td>所有在线玩家</td></tr>
<tr><td>connectObjects</td><td>connectId → ConnectObject</td><td>所有连接</td></tr>
<tr><td>humanIds</td><td>connectId → humanId</td><td>连接到角色的映射</td></tr>
<tr><td>uidAccounts</td><td>uid → accountId</td><td>UID 到账号 ID</td></tr>
<tr><td>uidPlays</td><td>uid → List&lt;HumanListInfo&gt;</td><td>UID 到角色列表</td></tr>
<tr><td>deleteHumanQueue</td><td>Queue</td><td>待删除角色队列</td></tr>
<tr><td>banHumanQueue</td><td>Queue</td><td>待封禁角色队列</td></tr>
<tr><td>muteHumanQueue</td><td>Queue</td><td>待禁言角色队列</td></tr>
</tbody>
</table>

<h2>玩家模块系统</h2>
<p>玩家存档按模块拆分，统一继承 <code>BaseModule</code>。每个模块通过 <code>putDbData(key, value)</code> / <code>getDbData(key, typeRef, callback)</code> 把数据存到 <code>human_info.role_data</code> 字段（MEDIUMBLOB），格式为 <strong>模块名 → JSON 字符串</strong>。</p>

<h3>模块生命周期</h3>
<table>
<thead><tr><th>方法</th><th>触发时机</th><th>说明</th></tr></thead>
<tbody>
<tr><td>init()</td><td>新角色创建时</td><td>初始化默认值，不能发协议</td></tr>
<tr><td>load()</td><td>老角色上线时</td><td>从 dataMap 恢复数据（getDbData 异步读取）</td></tr>
<tr><td>save()</td><td>定时存库或下线时</td><td>把内存数据写回 dataMap（putDbData）</td></tr>
<tr><td>sendToClient()</td><td>登录完成后</td><td>把模块数据同步给客户端</td></tr>
<tr><td>pulse()</td><td>每帧</td><td>模块定时逻辑</td></tr>
<tr><td>dailyReset()</td><td>跨天</td><td>每日重置</td></tr>
<tr><td>weekReset()</td><td>跨周</td><td>每周重置</td></tr>
</tbody>
</table>

<h3>已有模块</h3>
<table>
<thead><tr><th>模块</th><th>说明</th><th>关键数据</th></tr></thead>
<tbody>
<tr><td>DataModule</td><td>角色基础信息</td><td>名字/等级/经验/头像/战斗力/性别</td></tr>
<tr><td>ItemModule</td><td>背包系统</td><td>支持添加/删除/使用/出售/整理/堆叠</td></tr>
<tr><td>PlayerUnitModule</td><td>玩家场景单位</td><td>属性容器、当前位置、所在地图，整合了属性系统和地图相关数据</td></tr>
<tr><td>TaskModule</td><td>任务系统</td><td>任务列表、状态、进度</td></tr>
<tr><td>FriendModule</td><td>好友客户端侧</td><td>好友列表、申请列表</td></tr>
<tr><td>MailModule</td><td>邮件客户端侧</td><td>邮件列表、已读/未读</td></tr>
<tr><td>ActivityModule</td><td>活动系统</td><td>活动参与状态（如签到活动）</td></tr>
<tr><td>MinerModule</td><td>矿工玩法</td><td>绳子速度、钩子数量/力度、升级点数、关卡进度</td></tr>
<tr><td>CdkModule</td><td>兑换码</td><td>usedCodes：该角色已兑换的码集合，每码仅可兑换一次</td></tr>
</table>
<h2>协议路由</h2>
<h3>自动注册机制</h3>
<p>游戏服启动时执行两个初始化：</p>
<ol class="step-list">
    <li><code>ProtoParserUtils.init()</code> - 自动从 TOPIC 枚举提取 Proto 类名（TOPIC_TYPE_LOGIN → LoginProto），注册 FROM_CLIENT 枚举对应的 parseFrom 方法。key = packetType * 100000 + packetId</li>
    <li><code>LogicUtils.init()</code> - 扫描 @MsgHandlerClass + @MsgHandlerMethod，建立 packetType*10000+packetId → Method 映射</li>
</ol>

<h3>消息处理注解</h3>
<pre><code class="language-java">@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE)
public class ItemMsgHandler {

    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_UseItem_VALUE)
    public static void useItem(HumanObject humanObject, ItemProto.MC2S_UseItem data) {
        humanObject.getModule(ItemModule.class).useItem(data.getItemId(), data.getCount());
    }

    @MsgHandlerMethod(packetId = ItemProto.FROM_CLIENT.C2S_GetItemList_VALUE)
    public static void getList(HumanObject humanObject) {
        humanObject.getModule(ItemModule.class).sendAll();
    }
}</code></pre>

<h3>方法签名规则</h3>
<p>必须是 static 方法，参数只支持两种形态：</p>
<ul>
    <li>空消息：<code>public static void getList(HumanObject humanObject)</code></li>
    <li>带协议体：<code>public static void useItem(HumanObject humanObject, ItemProto.MC2S_UseItem data)</code></li>
</ul>

<h2>登录完整流程</h2>
<h3>玩家鉴权模式</h3>
<p>由 <code>player.auth.enabled</code> 控制（<code>game-config.properties</code> / <code>runallone-config.properties</code>，默认 <code>false</code>）：</p>
<table>
<thead><tr><th>模式</th><th>C2S_Login 入参</th><th>游戏服解析 uid</th></tr></thead>
<tbody>
<tr><td>关闭（开发）</td><td><code>uid</code> 字段</td><td>直接使用客户端传入的 uid</td></tr>
<tr><td>开启（生产）</td><td><code>token</code> 字段（HTTP <code>/login</code> 返回）</td><td><code>JwtUtil.verifyToken(token)</code> 解析 uid，Token 无效则拒绝登录</td></tr>
</tbody>
</table>
<p>游戏服启动时执行 <code>JwtUtil.init(properties)</code>，读取 <code>player.jwt.secret</code> 与 <code>player.jwt.expiration</code>，须与 Http 服配置一致。</p>

<ol class="step-list">
    <li>（可选，生产环境）客户端先调用 Http 服 <code>/login</code> 获取 JWT，再发送 <code>C2S_Login(token)</code>；未开启鉴权时发送 <code>C2S_Login(uid)</code></li>
    <li>客户端发送登录包（若触发排队则收到 <code>S2C_Queue</code>，出队后由服务端主动推送 <code>S2C_Login</code>，客户端可定时请求 <code>C2S_Login</code> 获取排队状态）</li>
    <li>Game 创建 ConnectObject，查询/创建 Account，返回 <code>S2C_Login(accountId)</code></li>
    <li>客户端发送 <code>C2S_HumanList</code></li>
    <li>Game 查询 human_list 表，返回 <code>S2C_HumanList</code>（角色列表）</li>
    <li>客户端发送 <code>C2S_SelectHuman(pos, serverId)</code></li>
    <li>新角色：插入 human_list 表 (uid, human_id, server_id, pos) → 创建 HumanObject → moduleInit() 新角色首次初始化 → S2C_SelectHuman() → sendHumanData() -> 插入 human_info 表 (human_id, role_data)</li>
    <li>老角色：匹配 human_list 表找到 human_id  → 检测 human_id 是否被封禁 → 重连 / 加载 human_info 表 → 创建 HumanObject → load(roleData) 加载所有模块数据 → sendHumanData()</li>
    <li>sendHumanData() 发送：S2C_HumanInfo + 各模块 sendToClient() + S2C_SendInfoEnd</li>
    <li>登录完成，需客户端定时发送 <code>C2S_ClientPing</code>（60秒超时判定玩家掉线，进行数据清理）</li>
</ol>

<h2>GameMasterService 心跳</h2>
<table>
<thead><tr><th>频率</th><th>行为</th></tr></thead>
<tbody>
<tr><td>pulse()</td><td>处理玩家消息队列 + 处理异步回调 + 系统心跳</td></tr>
<tr><td>pulsePerSec()</td><td>检测掉线（60秒无 Ping）+ 处理下线队列 + 定时存库（每分钟）+ 系统每秒心跳（含 LoginQueueSystem 登录出队）</td></tr>
</tbody>
</table>

<h2>GameRecvGmBackMessageService 心跳</h2>
<table>
<thead><tr><th>频率</th><th>行为</th></tr></thead>
<tbody>
<tr><td>pulsePerMin()</td><td>向GM后台节点定时上报game自身数据，用于后台页面展示</td></tr>
</tbody>
</table>

<h2>GameRecvGmBackMessageService GM 指令处理</h2>
<table>
<thead><tr><th>指令</th><th>说明</th></tr></thead>
<tbody>
<tr><td>reloadConfig</td><td>重新加载 Luban 配置表（ConfigUtils.load()）</td></tr>
<tr><td>kickHuman</td><td>踢玩家下线（加入下线队列）</td></tr>
<tr><td>banHumanList</td><td>更新封禁名单（加入 banHumanQueue）</td></tr>
<tr><td>muteHumanList</td><td>更新禁言名单（加入 muteHumanQueue）</td></tr>
<tr><td>hotswapJar</td><td>代码热更 JAR（HotswapScanner）</td></tr>
<tr><td>cdkList</td><td>同步当前有效兑换码列表到 CdkSystem</td></tr>
</tbody>
</table>
`);

registerPage('global-server', '全局服', '聊天、好友、邮件、玩家信息跨服服务', () => `
<h1>全局服</h1>
<p class="page-desc">GlobalServer 承接跨玩家、跨逻辑服共享的公共能力。所有需要跨服共享的数据和逻辑都应放到 Global 服</p>

<h2>核心职责</h2>
<ul>
    <li>跨逻辑服访问（聊天、好友、邮件需要全服共享）</li>
    <li>全服广播（聊天消息、好友通知）</li>
    <li>所有玩家共享同一份数据（好友关系、邮件、聊天历史）</li>
</ul>

<h2>已有服务</h2>
<div class="card-grid">
    <div class="card"><div class="card-icon">💬</div><div class="card-title">GlobalChatService</div><div class="card-desc">聊天历史与广播。维护最多50条聊天记录，game 调用后返回已编码的 protobuf 二进制，game 直接转发给客户端</div></div>
    <div class="card"><div class="card-icon">👥</div><div class="card-title">GlobalFriendService</div><div class="card-desc">好友关系与申请。处理好友申请、获取列表、发送申请、删除好友。7天过期清理申请</div></div>
    <div class="card"><div class="card-icon">📬</div><div class="card-title">GlobalMailService</div><div class="card-desc">邮件与附件。支持单发、群发、全服发、领取附件、读取、删除。未领取附件不可删除</div></div>
    <div class="card"><div class="card-icon">👤</div><div class="card-title">GlobalPlayerInfoService</div><div class="card-desc">玩家简要信息。获取/更新/批量获取玩家信息（名字/等级/头像/战斗力/性别）</div></div>
</div>

<h2>GlobalChatService 详解</h2>
<table>
<thead><tr><th>CallEnum</th><th>方法签名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>GlobalChatService_chat</td><td>chat(humanId, message)</td><td>发送聊天消息，添加到历史（最多50条），SendAll 广播 GameRpcListenService_sendToAllHuman</td></tr>
<tr><td>GlobalChatService_history</td><td>history(humanId)</td><td>获取聊天历史，返回已编码的 protobuf 二进制（game 直接转发给客户端）</td></tr>
</tbody>
</table>
<pre><code class="language-java">@RpcService
public class GlobalChatService extends BaseService {
    private List&lt;ChatRecord&gt; chatHistory = new ArrayList&lt;&gt;();
    private static final int MAX_HISTORY = 50;

    @RpcMethod
    public void chat(String humanId, String message) {
        ChatRecord record = new ChatRecord(humanId, message, System.currentTimeMillis());
        chatHistory.add(record);
        if (chatHistory.size() > MAX_HISTORY) {
            chatHistory.remove(0);
        }
        // 广播给所有 Game 节点
        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.GameRpcListenService_sendToAllHuman, TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_Chat_VALUE,
                        ChatProto.MS2C_Chat.newBuilder().setId(humanId).setName(name).setMsg(message).setTime(time).build().toByteArray());
    }

    @RpcMethod
    public void history(String humanId) {
        // 构建 protobuf 二进制
        byte[] protoData = buildChatHistoryProto(chatHistory);
        returns("info", protoData);
    }
}</code></pre>

<h2>GlobalFriendService 详解</h2>
<table>
<thead><tr><th>CallEnum</th><th>说明</th><th>校验逻辑</th></tr></thead>
<tbody>
<tr><td>GlobalFriendService_sendFriendRequest</td><td>发送好友申请</td><td>检查：不能加自己、不能重复申请、已是好友、申请列表上限</td></tr>
<tr><td>GlobalFriendService_handleFriendRequest</td><td>处理好友申请（同意/拒绝）</td><td>同意后双向广播 FriendRpcListenService_onFriendAdded</td></tr>
<tr><td>GlobalFriendService_deleteFriend</td><td>删除好友</td><td>双向广播 FriendRpcListenService_onFriendDeleted</td></tr>
<tr><td>GlobalFriendService_getFriends</td><td>获取好友列表</td><td>返回好友 humanId 列表</td></tr>
<tr><td>GlobalFriendService_getFriendRequests</td><td>获取好友申请列表</td><td>返回待处理的申请</td></tr>
</tbody>
</table>
<p>pulse() 中自动清理超过 7 天的好友申请（cleanExpiredRequests）。</p>

<h2>GlobalMailService 详解</h2>
<table>
<thead><tr><th>CallEnum</th><th>说明</th><th>特殊说明</th></tr></thead>
<tbody>
<tr><td>GlobalMailService_sendMail</td><td>发送单人邮件</td><td>构建 proto 二进制，通过 GameRpcListenService 通知目标玩家</td></tr>
<tr><td>GlobalMailService_sendGroupMail</td><td>群发邮件</td><td>遍历 humanIds 逐个创建</td></tr>
<tr><td>GlobalMailService_sendAllMail</td><td>全服发送邮件</td><td>先调 GlobalPlayerInfoService_getAllPlayerIds 获取所有玩家 ID</td></tr>
<tr><td>GlobalMailService_getMailList</td><td>获取邮件列表</td><td>返回 proto 二进制</td></tr>
<tr><td>GlobalMailService_readMail</td><td>标记已读</td><td>-</td></tr>
<tr><td>GlobalMailService_claimAttachment</td><td>领取附件</td><td>返回附件 JSON，标记已领取</td></tr>
<tr><td>GlobalMailService_deleteMail</td><td>删除邮件</td><td>未领取附件不可删除</td></tr>
</tbody>
</table>

<h2>GlobalPlayerInfoService 详解</h2>
<table>
<thead><tr><th>CallEnum</th><th>说明</th></tr></thead>
<tbody>
<tr><td>GlobalPlayerInfoService_getPlayerInfo</td><td>获取单个玩家信息（名字/等级/头像/战斗力/性别）</td></tr>
<tr><td>GlobalPlayerInfoService_updatePlayerInfo</td><td>更新玩家信息（Game 服登录/变化时调用）</td></tr>
<tr><td>GlobalPlayerInfoService_getPlayerInfos</td><td>批量获取玩家信息（好友列表场景）</td></tr>
<tr><td>GlobalPlayerInfoService_getAllHumanIds</td><td>获取所有角色 ID（全服邮件场景）</td></tr>
</tbody>
</table>

<h2>跨服通知机制</h2>
<p>Global 服处理完后，需要通知玩家所在的 Game 节点，使用 <code>SendAll</code> 广播给所有 Game 节点：</p>
<p><code>CallEnum.GameRpcListenService_sendToAllHuman</code> 广播给所有游戏服所有玩家</p>
<p><code>CallEnum.GameRpcListenService_sendToHuman</code> 广播给所有游戏服，每个游戏服各自判断是否持有目标玩家，进行发送</p>
<p><code>CallEnum.FriendRpcListenService_onNewFriendRequest</code> / <code>onFriendAdded</code> / <code>onFriendDeleted</code> 好友事件通知游戏服</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-arrow">→ RPC SendRandom →</span>
        <span class="flow-node flow-node-warning">Global</span>
        <span class="flow-arrow">→ 处理业务 →</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-warning">Global</span>
        <span class="flow-arrow">→ RPC SendAll →</span>
        <span class="flow-node flow-node-secondary">Game A/B/C</span>
        <span class="flow-arrow">→ 各自判断是否持有目标玩家 →</span>
        <span class="flow-node flow-node-primary">Client</span>
    </div>
</div>
<p>每个 Game 节点各自判断是否持有目标玩家，如果持有则推送消息。这是"广播 + 本地判断"的经典模式。</p>
`);

registerPage('http-server', 'HTTP 服务', '邮箱注册登录、对外服地址分配、心跳上报', () => `
<h1>HTTP 服务</h1>
<p class="page-desc">HttpServer 基于 Javalin 轻量级 Web 框架，为客户端提供邮箱注册/登录、对外服地址分配与运营接口</p>

<h2>核心职责</h2>
<ul>
    <li>邮箱注册/登录/找回密码（验证码邮件 + JWT Token）</li>
    <li>提供对外服地址查询接口（同一 uid 优先分配之前的 external）</li>
    <li>接收对外服的心跳上报（每5秒）</li>
    <li>提供服务器开关、白名单与公告接口</li>
    <li>提供 KCP conv ID 分配接口</li>
</ul>

<h2>玩家鉴权流程</h2>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ POST /send_code →</span>
        <span class="flow-node flow-node-warning">HttpServer</span>
        <span class="flow-arrow">→ SMTP 邮件 →</span>
        <span class="flow-node flow-node-primary">邮箱</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ POST /register 或 /login →</span>
        <span class="flow-node flow-node-warning">HttpServer</span>
        <span class="flow-arrow">→ JWT Token →</span>
        <span class="flow-node flow-node-secondary">Client 缓存 token</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-primary">Client</span>
        <span class="flow-arrow">→ GET /external_address（Header: Authorization）→</span>
        <span class="flow-node flow-node-warning">HttpServer</span>
        <span class="flow-arrow">→ 连接对外服 → C2S_Login(token) →</span>
        <span class="flow-node flow-node-secondary">GameServer</span>
    </div>
</div>
<p>注册成功后由http服务生成 uid 并绑定邮箱。重置密码会使该用户历史 Token 失效。</p>

<h2>HTTP 接口</h2>
<h3>玩家认证（POST，Query 参数）</h3>
<table>
<thead><tr><th>接口</th><th>方法</th><th>参数</th><th>返回</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/send_code</td><td>POST</td><td>email</td><td>{"result":true}</td><td>发送邮箱验证码（5 分钟有效，同邮箱 1 分钟限发一次）</td></tr>
<tr><td>/register</td><td>POST</td><td>email, password, code</td><td>{"result":true} 或 {"result":false,"msg":"..."}</td><td>邮箱注册，密码至少 6 位，验证码校验通过后创建账户</td></tr>
<tr><td>/login</td><td>POST</td><td>email, password</td><td>{"result":true,"token":"..."}</td><td>邮箱登录，Token 的 subject 为 uid</td></tr>
<tr><td>/forgot_password</td><td>POST</td><td>email, password, code</td><td>{"result":true}</td><td>验证码通过后重置密码，并使该用户旧 Token 失效</td></tr>
</tbody>
</table>

<h3>地址与运营（GET）</h3>
<table>
<thead><tr><th>接口</th><th>方法</th><th>参数 / Header</th><th>返回</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/server_status</td><td>GET</td><td>uid 或 Authorization</td><td>{"open":true}</td><td>查询服务器开关；uid 用于白名单判断</td></tr>
<tr><td>/external_address</td><td>GET</td><td>type, uid 或 Authorization</td><td>{"address":"127.0.0.1:10000"}</td><td>分配对外服地址；开启鉴权时须携带有效 Token</td></tr>
<tr><td>/external_address_list</td><td>GET</td><td>-</td><td>[{address,type,id}]</td><td>获取所有对外服地址</td></tr>
<tr><td>/kcp_conv</td><td>GET</td><td>-</td><td>{"conv":12345}</td><td>分配 KCP conv ID</td></tr>
<tr><td>/announcements</td><td>GET</td><td>-</td><td>[{id,title,content,startTime,endTime}]</td><td>获取当前生效的公告列表</td></tr>
</tbody>
</table>

<h3>resolveRequestUid 规则</h3>
<p><code>/server_status</code> 与 <code>/external_address</code> 通过 <code>resolveRequestUid()</code> 解析请求方 uid：</p>
<ul>
    <li><code>player.auth.enabled=true</code>：必须携带 <code>Authorization</code> Header（Bearer Token 或裸 Token），仅 Token 有效时返回 uid</li>
    <li><code>player.auth.enabled=false</code>：Token 有效则解析 uid；否则回退到 query 参数 <code>uid</code>（兼容开发直连）</li>
</ul>

<h3>地址分配逻辑</h3>
<pre><code class="language-text">GET /external_address?type=tcp&uid=xxx

1. 检查 uid 是否有之前分配过的 external 地址
2. 如果有且该 external 仍然可用 → 返回旧地址
3. 如果旧地址不可用 → 从地址池随机分配一个新的
4. 如果没有旧地址 → 随机分配</code></pre>

<h3>为什么需要分配 KCP convid（会话 ID）</h3>
<p>KCP 是一个纯应用层实现的可靠传输协议，基于 UDP 协议运行。而 UDP 本身是无连接、无状态的，
KCP 要在 UDP 之上实现类似 TCP 的可靠传输（确认、重传、流量控制、拥塞控制），就必须自己模拟 "连接" 的概念，
而 convid 就是 KCP 用来唯一标识一个 "连接" 的核心机制。
所以我们通过http服务管理连接id，目前仅仅用了AtomicInteger 自增简单实现分配，可根据需求进行修改。</p>

<h2>核心类</h2>
<table>
<thead><tr><th>类名</th><th>说明</th></tr></thead>
<tbody>
<tr><td>HttpServer</td><td>基于 Javalin 的 HTTP 服务，注册认证与地址分配接口</td></tr>
<tr><td>HttpServer.AuthUser</td><td>玩家账户（uid、email、passwordHash），内存 Map 按邮箱索引</td></tr>
<tr><td>HttpRecvMessageService</td><td>@RpcService，管理对外服地址与 authUsers 持久化，每 5 秒更新地址映射</td></tr>
<tr><td>MailUtil</td><td>SMTP 验证码发送与校验（network 模块）</td></tr>
<tr><td>PasswordUtil</td><td>密码 SHA-256 哈希（network 模块）</td></tr>
</tbody>
</table>

<h2>RPC 方法</h2>
<table>
<thead><tr><th>CallEnum</th><th>说明</th></tr></thead>
<tbody>
<tr><td>HttpRecvMessageService_updateExternalRemoteData</td><td>更新对外服地址数据（External 上报）</td></tr>
<tr><td>HttpRecvMessageService_setExternalServerStatus</td><td>设置服务器开关状态（GM 后台调用）</td></tr>
<tr><td>HttpRecvMessageService_setWhitelist</td><td>设置白名单（GM 后台调用）</td></tr>
<tr><td>HttpRecvMessageService_setAnnouncements</td><td>设置当前生效公告列表（GM 后台调用）</td></tr>
</tbody>
</table>

<h2>配置项</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>http.port</td><td>HTTP 服务端口</td><td>8090</td></tr>
<tr><td>player.auth.enabled</td><td>是否强制玩家 Token 鉴权（须与 game-config 一致）</td><td>false</td></tr>
<tr><td>player.jwt.secret</td><td>玩家 JWT 密钥（≥32 字符，生产环境务必修改）</td><td>sunrise-player-jwt-secret-change-me-in-production-32b</td></tr>
<tr><td>player.jwt.expiration</td><td>玩家 JWT 过期时间（毫秒）</td><td>86400000</td></tr>
<tr><td>mail.smtp.username</td><td>SMTP 发件邮箱（QQ 邮箱等）</td><td>your@qq.com</td></tr>
<tr><td>mail.smtp.password</td><td>SMTP 授权码（非登录密码）</td><td>xxxx</td></tr>
</tbody>
</table>
<div class="callout callout-info">
    <p><strong>💡 启动初始化</strong>：<code>HttpServerStartUp</code> 与 <code>RunAllOneServerStartUp</code> 启动时调用 <code>JwtUtil.init(properties)</code> 加载玩家 JWT 配置；<code>GameServerStartUp</code> 同样需初始化以便校验 <code>C2S_Login</code> Token。</p>
</div>
`);

registerPage('gmback-server', 'GM 后台', 'gmback API、gmback-ui SPA、权限与 Nginx 部署', () => `
<h1>GM 后台</h1>
<p class="page-desc">后端 <code>GmBackServer</code>（Javalin + JWT）提供 REST API 并与 Game 服同步 GM 指令；前端 <code>gmback-ui</code>（Vue 3 SPA）独立部署，通过同源 <code>/api</code> 访问后端</p>

<h2>整体架构</h2>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-primary">浏览器</span>
        <span class="flow-arrow">→ 开发环境5173端口 / 生产环境Nginx →</span>
        <span class="flow-node flow-node-primary">gmback-ui<br/>dist/</span>
    </div>
    <div class="flow-row">
        <span class="flow-node flow-node-primary">gmback-ui</span>
        <span class="flow-arrow">→ fetch /api/* →</span>
        <span class="flow-node flow-node-danger">GmBackServer<br/>admin.port</span>
        <span class="flow-arrow">→ RPC SendAll / 定向 →</span>
        <span class="flow-node flow-node-secondary">Game / Http …</span>
    </div>
</div>
<ul>
    <li><strong>gmback</strong>（<code>game/.../gmback</code>）：不托管静态页面，只监听 <code>admin.port</code>（默认 8010），路径均为 <code>/api/...</code></li>
    <li><strong>gmback-ui</strong>（仓库根目录）：Vite + Vue Router（History 模式）单页应用；请求使用相对路径 <code>/api/...</code>，需与页面同源（开发靠 Vite 代理，生产靠 Nginx 反代）</li>
    <li>鉴权：登录后 JWT 存于 <code>localStorage</code>，<code>apiFetch</code> 自动附加 <code>Authorization</code> 头；除 <code>/api/login</code> 外均需有效 Token</li>
    <li>页面权限：非管理员账号按 <code>permissions</code> 控制侧栏菜单与 API（<code>PermissionHelper</code>）</li>
</ul>

<h2>GmBackServer 核心职责</h2>
<ul>
    <li>JWT 登录与会话（<code>/api/login</code>、<code>/api/auth/info</code>）</li>
    <li>节点监控（<code>/api/nodes</code>）</li>
    <li>配置热更（<code>/api/config/reload</code> → 广播 reloadConfig）</li>
    <li>代码热更（<code>/api/hotswap/jar</code> → 广播 hotswapJar，详见 <a href="#/hotswap">代码热更</a>）</li>
    <li>运营：发邮件、踢人、封禁/禁言、在线玩家、服务器开关、白名单、公告、兑换码</li>
    <li>后台用户、页面权限、操作日志</li>
</ul>

<h2>REST API（GmBackServer）</h2>
<p>以下与 <code>AdminServer.java</code> 注册路由一致。鉴权列「需要」表示需有效 JWT（<code>Authorization</code> 头）。</p>
<table>
<thead><tr><th>接口</th><th>方法</th><th>说明</th><th>鉴权</th></tr></thead>
<tbody>
<tr><td>/api/login</td><td>POST</td><td>登录，返回 JWT</td><td>无需</td></tr>
<tr><td>/api/auth/info</td><td>GET</td><td>当前会话（权限列表、是否管理员）</td><td>需要</td></tr>
<tr><td>/api/nodes</td><td>GET</td><td>所有 RPC 节点状态</td><td>需要</td></tr>
<tr><td>/api/config/reload</td><td>POST</td><td>热更 Luban 配置（广播 reloadConfig）</td><td>需要</td></tr>
<tr><td>/api/hotswap/jar</td><td>POST</td><td>代码热更 JAR 路径</td><td>需要</td></tr>
<tr><td>/api/gm/send-mail</td><td>POST</td><td>发送邮件</td><td>需要</td></tr>
<tr><td>/api/gm/kick</td><td>POST</td><td>踢玩家下线</td><td>需要</td></tr>
<tr><td>/api/ban/list</td><td>GET</td><td>封禁列表</td><td>需要</td></tr>
<tr><td>/api/ban</td><td>POST</td><td>添加封禁</td><td>需要</td></tr>
<tr><td>/api/unban</td><td>POST</td><td>解除封禁</td><td>需要</td></tr>
<tr><td>/api/mute/list</td><td>GET</td><td>禁言列表</td><td>需要</td></tr>
<tr><td>/api/mute</td><td>POST</td><td>添加禁言</td><td>需要</td></tr>
<tr><td>/api/unmute</td><td>POST</td><td>解除禁言</td><td>需要</td></tr>
<tr><td>/api/online-players</td><td>GET</td><td>在线玩家列表</td><td>需要</td></tr>
<tr><td>/api/server-status</td><td>GET/POST</td><td>服务器开关</td><td>需要</td></tr>
<tr><td>/api/whitelist</td><td>GET/POST</td><td>白名单列表 / 添加</td><td>需要</td></tr>
<tr><td>/api/whitelist/remove</td><td>POST</td><td>移除白名单</td><td>需要</td></tr>
<tr><td>/api/announcements</td><td>GET/POST</td><td>公告列表 / 发布</td><td>需要</td></tr>
<tr><td>/api/announcements/update</td><td>POST</td><td>修改公告</td><td>需要</td></tr>
<tr><td>/api/announcements/remove</td><td>POST</td><td>删除公告</td><td>需要</td></tr>
<tr><td>/api/cdk</td><td>GET/POST</td><td>兑换码列表 / 创建</td><td>需要</td></tr>
<tr><td>/api/cdk/update</td><td>POST</td><td>修改兑换码</td><td>需要</td></tr>
<tr><td>/api/cdk/adjust-count</td><td>POST</td><td>增减兑换码数量</td><td>需要</td></tr>
<tr><td>/api/cdk/remove</td><td>POST</td><td>删除兑换码</td><td>需要</td></tr>
<tr><td>/api/users</td><td>GET/POST</td><td>用户列表 / 新增</td><td>需要（管理员）</td></tr>
<tr><td>/api/users/{username}</td><td>DELETE</td><td>删除用户</td><td>需要（管理员）</td></tr>
<tr><td>/api/users/{username}/password</td><td>PUT</td><td>修改密码</td><td>需要（管理员）</td></tr>
<tr><td>/api/users/{username}/permissions</td><td>GET/PUT</td><td>页面权限</td><td>需要（管理员）</td></tr>
<tr><td>/api/permission/pages</td><td>GET</td><td>可分配的页面列表</td><td>需要（管理员）</td></tr>
<tr><td>/api/logs</td><td>GET</td><td>操作日志</td><td>需要</td></tr>
</tbody>
</table>

<h2>GM 指令广播</h2>
<p>部分运营操作在 GmBack 收到 REST 请求后，通过 RPC <code>SendAll</code> 或定向调用下发到 Game 节点（如 reloadConfig、hotswapJar、kickHuman、banHumanList、muteHumanList、cdkList）：</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-danger">gmback-ui</span>
        <span class="flow-arrow">→ /api/* →</span>
        <span class="flow-node flow-node-danger">GmBackServer</span>
        <span class="flow-arrow">→ RPC SendAll →</span>
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-node flow-node-secondary">Game B</span>
        <span class="flow-node flow-node-secondary">Game C</span>
    </div>
</div>
<p>每个 Game 节点在 <code>GameRecvGmBackMessageService</code> 中处理指令；兑换码兑换成功后 Game 可反向上报 <code>cdkRedeem</code> 至 GmBack。</p>

<h2>gmback-ui 前端</h2>
<p>独立 npm 工程，技术栈：<strong>Vue 3 + Vue Router + Element Plus + Tailwind CSS + Vite</strong></p>

<h3>开发与访问</h3>
<pre><code class="language-bash"># 先启动 gmback（GmBackServer 或 RunAllOne，admin.port 默认 8010）
cd gmback-ui
npm install
npm run dev</code></pre>
<p>浏览器打开 <code>http://localhost:5173/</code>。Vite 将 <code>/api</code> 代理到 <code>.env.development</code> 中的 <code>VITE_API_PROXY_TARGET</code>（默认 <code>http://127.0.0.1:8010</code>）。默认账号见 <code>admin.user</code> / <code>admin.password</code>（如 admin / sunrise）。</p>

<h3>生产构建与 Nginx</h3>
<pre><code class="language-bash">cd gmback-ui
npm run build   # 产物在 dist/</code></pre>
<p>生产环境由 Nginx 托管 <code>dist/</code>，并将 <code>/api/</code> 反代到本机 <code>admin.port</code>（与页面同源）。SPA 需配置 <code>try_files $uri $uri/ /index.html;</code>。</p>
<pre><code class="language-nginx">server {
    listen 80;
    server_name gm.example.com;   # 改为你的域名或 _

    root /var/www/gmback-ui/dist;
    index index.html;

    # Vue Router History：先找静态文件，否则回退 SPA 入口
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 所有 API 转发到 gmback（保留 /api 前缀，勿在 proxy_pass 末尾加 /）
    location /api/ {
        proxy_pass http://127.0.0.1:8010;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # 前端通过 Authorization 头传递 JWT，无需额外配置
    }
}</code></pre>

<p>HTTPS：在 80 上跳转 443，并为 443 使用与上文相同的 \`root\`、\`location /\`、\`location /api/\`：</p>
<pre><code class="language-nginx">server {
    listen 80;
    server_name gm.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name gm.example.com;

    ssl_certificate     /etc/nginx/cert/fullchain.pem;
    ssl_certificate_key /etc/nginx/cert/privkey.pem;

    root /var/www/gmback-ui/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8010;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}</code></pre>

<h3>功能页面（路由）</h3>
<table>
<thead><tr><th>路由</th><th>权限 key</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/monitor</td><td>monitor</td><td>节点监控</td></tr>
<tr><td>/server-status</td><td>server_status</td><td>服务器关闭</td></tr>
<tr><td>/online-player</td><td>online_player</td><td>在线玩家</td></tr>
<tr><td>/config-update</td><td>config_update</td><td>配置更新</td></tr>
<tr><td>/hotswap-jar</td><td>hotswap_jar</td><td>代码热更</td></tr>
<tr><td>/send-mail</td><td>send_mail</td><td>发送邮件</td></tr>
<tr><td>/kick-human</td><td>kick_human</td><td>玩家下线</td></tr>
<tr><td>/ban-player</td><td>ban_player</td><td>玩家封禁</td></tr>
<tr><td>/mute-player</td><td>mute_player</td><td>玩家禁言</td></tr>
<tr><td>/whitelist</td><td>whitelist</td><td>白名单</td></tr>
<tr><td>/announcement</td><td>announcement</td><td>全服公告</td></tr>
<tr><td>/cdk</td><td>cdk</td><td>兑换码</td></tr>
<tr><td>/operation-log</td><td>operation_log</td><td>操作记录</td></tr>
<tr><td>/user-manager</td><td>user_manager</td><td>用户管理（仅管理员）</td></tr>
</tbody>
</table>
<p>新增页面：在 <code>src/views/</code> 添加 Vue 组件，并在 <code>menu.js</code> 的 <code>MENU_ITEMS</code> 增加一项；路由由 <code>router/routes.js</code> 自动生成，一般无需改 Java 后端。</p>

<h3>目录结构</h3>
<pre><code class="language-bash">gmback-ui/
├── index.html              # 唯一 HTML 入口
├── vite.config.js
├── .env.development
├── .env.production
└── src/
    ├── main.js             # 应用入口
    ├── App.vue             # 根组件（会话门闸）
    ├── api/
    │   └── client.js       # apiFetch、鉴权 token
    ├── assets/
    │   └── styles/         # 全局样式（Tailwind + EP + 布局/业务）
    ├── components/
    │   └── layout/         # 侧栏、顶栏等可复用布局组件
    ├── composables/
    │   └── useAuth.js      # 登录、权限、会话
    ├── constants/
    │   ├── menu.js         # 菜单与路由元数据
    │   └── menu-icons.js
    ├── layouts/
    │   └── MainLayout.vue  # 登录后主壳
    ├── plugins/
    │   └── element-plus.js # 按需注册 EP 组件
    ├── router/
    │   ├── index.js
    │   ├── routes.js
    │   └── guards.js
    ├── utils/index.js      # 工具函数（API 响应、分页、对话框等，统一导出）
    └── views/              # 业务页面（按路由懒加载）
        ├── auth/
        │   └── LoginView.vue
        ├── Monitor.vue
        └── ...</code></pre>

<h2>配置项（gmback / runallone）</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>admin.port</td><td>GM 后台端口</td><td>8010</td></tr>
<tr><td>admin.user</td><td>登录用户名</td><td>admin</td></tr>
<tr><td>admin.password</td><td>登录密码</td><td>sunrise</td></tr>
<tr><td>admin.jwt.expiration</td><td>JWT 过期时间（毫秒）</td><td>86400000</td></tr>
</tbody>
</table>
`);
