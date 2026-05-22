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
    <li>响应 GM 后台命令（reloadConfig/hotswapJar/kick/ban/mute）</li>
</ul>

<h2>GameServerStartUp 启动流程</h2>
<pre><code class="language-java">public class GameServerStartUp {
    public static void main(String[] args) {
        ConfigReader.loadConfig("game-config.properties");
        RpcNode rpcNode = RpcNodeManager.createRpcNode(1000);
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
            "connectionId", connectId,
            "data", bytes,
            "gameNodeId", gameNodeId  // 首次发送带 gameNodeId
        );
    }
}</code></pre>

<h3>HumanObjectManger</h3>
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
<tr><td>MapModule</td><td>地图模块</td><td>当前地图 ID、坐标、朝向</td></tr>
<tr><td>TaskModule</td><td>任务系统</td><td>任务列表、状态、进度</td></tr>
<tr><td>FriendModule</td><td>好友客户端侧</td><td>好友列表、申请列表</td></tr>
<tr><td>MailModule</td><td>邮件客户端侧</td><td>邮件列表、已读/未读</td></tr>
<tr><td>ActivityModule</td><td>活动系统</td><td>活动参与状态</td></tr>
<tr><td>AttributeModule</td><td>属性系统</td><td>玩家属性值</td></tr>
<tr><td>MinerModule</td><td>矿工玩法</td><td>挖矿状态</td></tr>
</tbody>
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
<ol class="step-list">
    <li>客户端发送 <code>C2S_Login(uid)</code>（若触发排队则收到 <code>S2C_Queue</code>，出队后由服务端主动推送 <code>S2C_Login</code>，客户端可定时请求<code>C2S_Login</code>获取排队状态）</li>
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
                .call(CallEnum.GameRpcListenService_sendToAllHuman, "packetType", TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, "packetId", ChatProto.FROM_SERVER.S2C_Chat_VALUE,
                        "time", ChatProto.MS2C_Chat.newBuilder().setId(humanId).setMsg(message).setTime(time).build().toByteArray());
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
<tr><td>GlobalMailService_sendMail</td><td>发送单人邮件</td><td>构建 proto 二进制，SendAll 广播 MailRpcListenService_onNewMail</td></tr>
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

registerPage('http-server', 'HTTP 服务', '对外服地址分配、心跳上报', () => `
<h1>HTTP 服务</h1>
<p class="page-desc">HttpServer 基于 Javalin 轻量级 Web 框架，主要给客户端分配对外服地址</p>

<h2>核心职责</h2>
<ul>
    <li>提供对外服地址查询接口（同一 uid 优先分配之前的 external）</li>
    <li>接收对外服的心跳上报（每5秒）</li>
    <li>提供服务器开关和白名单接口</li>
    <li>提供 KCP conv ID 分配接口</li>
</ul>

<h2>HTTP 接口</h2>
<table>
<thead><tr><th>接口</th><th>方法</th><th>参数</th><th>返回</th><th>说明</th></tr></thead>
<tbody>
<tr><td>/server_status</td><td>GET</td><td>uid</td><td>{"open":true}</td><td>查询服务器开关状态，uid用于白名单判断</td></tr>
<tr><td>/external_address</td><td>GET</td><td>type, uid</td><td>{"address":"127.0.0.1:10000"}</td><td>分配对外服地址</td></tr>
<tr><td>/external_address_list</td><td>GET</td><td>-</td><td>{"addresses":[...]}</td><td>获取所有对外服地址</td></tr>
<tr><td>/kcp_conv</td><td>GET</td><td>-</td><td>{"conv":12345}</td><td>分配 KCP conv ID</td></tr>
<tr><td>/announcements</td><td>GET</td><td>-</td><td>[{id,title,content,startTime,endTime}]</td><td>获取当前生效的公告列表，客户端通过curl请求</td></tr>
</tbody>
</table>

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
<tr><td>HttpServer</td><td>基于 Javalin 的 HTTP 服务，提供 4 个接口</td></tr>
<tr><td>HttpRecvMessageService</td><td>@RpcService，管理对外服地址数据，每 5 秒更新 HttpServer 的地址映射</td></tr>
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
</tbody>
</table>
`);

registerPage('gmback-server', 'GM 后台', '登录认证、节点监控、配置热更、封禁禁言', () => `
<h1>GM 后台</h1>
<p class="page-desc">GmBackServer 提供后台登录与管理接口，基于 Javalin + JWT 鉴权，也负责和 Game 服做 GM 指令同步</p>

<h2>核心职责</h2>
<ul>
    <li>JWT 登录认证（HS256 签名 + Token 黑名单）</li>
    <li>节点监控（查看所有 RPC 节点状态）</li>
    <li>热更配置（广播 reloadConfig 给所有 Game 节点）</li>
    <li>代码热更（广播 hotswapJar，通过 JVM Agent redefine 类）</li>
    <li>发送邮件（单人/群发/全服）</li>
    <li>踢人下线（广播 kickHuman）</li>
    <li>封禁/禁言名单广播（banHumanList/muteHumanList）</li>
    <li>全服公告管理（发布/编辑/删除，定时同步到HttpServer）</li>
    <li>用户与操作日志管理</li>
</ul>

<h2>REST API</h2>
<table>
<thead><tr><th>接口</th><th>方法</th><th>说明</th><th>鉴权</th></tr></thead>
<tbody>
<tr><td>/api/login</td><td>POST</td><td>登录认证，返回 JWT Token</td><td>无需</td></tr>
<tr><td>/api/nodes</td><td>GET</td><td>获取所有节点状态</td><td>需要</td></tr>
<tr><td>/api/gm/send-mail</td><td>POST</td><td>发送邮件</td><td>需要</td></tr>
<tr><td>/api/gm/kick</td><td>POST</td><td>踢玩家下线</td><td>需要</td></tr>
<tr><td>/api/config/reload</td><td>POST</td><td>热更配置</td><td>需要</td></tr>
<tr><td>/api/hotswap/jar</td><td>POST</td><td>代码热更 JAR</td><td>需要</td></tr>
<tr><td>/api/ban/add</td><td>POST</td><td>添加封禁</td><td>需要</td></tr>
<tr><td>/api/ban/remove</td><td>POST</td><td>解除封禁</td><td>需要</td></tr>
<tr><td>/api/ban/list</td><td>GET</td><td>封禁列表</td><td>需要</td></tr>
<tr><td>/api/mute/add</td><td>POST</td><td>添加禁言</td><td>需要</td></tr>
<tr><td>/api/mute/remove</td><td>POST</td><td>解除禁言</td><td>需要</td></tr>
<tr><td>/api/mute/list</td><td>GET</td><td>禁言列表</td><td>需要</td></tr>
<tr><td>/api/online-players</td><td>GET</td><td>在线玩家列表</td><td>需要</td></tr>
<tr><td>/api/server-status</td><td>GET/POST</td><td>服务器开关</td><td>需要</td></tr>
<tr><td>/api/whitelist</td><td>GET/POST</td><td>白名单管理</td><td>需要</td></tr>
<tr><td>/api/announcements</td><td>GET/POST</td><td>公告列表/发布公告</td><td>需要</td></tr>
<tr><td>/api/announcements/update</td><td>POST</td><td>修改公告</td><td>需要</td></tr>
<tr><td>/api/announcements/remove</td><td>POST</td><td>删除公告</td><td>需要</td></tr>
<tr><td>/api/users</td><td>GET</td><td>用户管理</td><td>需要</td></tr>
<tr><td>/api/logs</td><td>GET</td><td>操作日志</td><td>需要</td></tr>
</tbody>
</table>

<h2>GM 指令广播</h2>
<p>GM 后台通过 <code>SendAll</code> 广播指令给所有 Game 节点：</p>
<div class="flow-diagram">
    <div class="flow-row">
        <span class="flow-node flow-node-danger">GM 后台</span>
        <span class="flow-arrow">→ REST API →</span>
        <span class="flow-node flow-node-danger">GmBackServer</span>
        <span class="flow-arrow">→ RPC SendAll →</span>
        <span class="flow-node flow-node-secondary">Game A</span>
        <span class="flow-node flow-node-secondary">Game B</span>
        <span class="flow-node flow-node-secondary">Game C</span>
    </div>
</div>
<p>每个 Game 节点根据本地在线玩家情况执行对应动作。</p>

<h2>Admin UI</h2>
<p>GM 后台前端基于 Vue 3 + Element Plus + Tailwind CSS，通过 iframe 嵌入各功能页面：</p>
<table>
<thead><tr><th>页面</th><th>说明</th></tr></thead>
<tbody>
<tr><td>monitor.html</td><td>节点监控</td></tr>
<tr><td>server_status.html</td><td>服务器开关/白名单</td></tr>
<tr><td>online_player.html</td><td>在线玩家</td></tr>
<tr><td>config_update.html</td><td>配置热更</td></tr>
<tr><td>hotswap_jar.html</td><td>代码热更 JAR</td></tr>
<tr><td>send_mail.html</td><td>发送邮件</td></tr>
<tr><td>kick_human.html</td><td>踢人下线</td></tr>
<tr><td>ban_player.html</td><td>封禁管理</td></tr>
<tr><td>mute_player.html</td><td>禁言管理</td></tr>
<tr><td>whitelist.html</td><td>白名单管理</td></tr>
<tr><td>announcement.html</td><td>全服公告管理</td></tr>
<tr><td>operation_log.html</td><td>操作日志</td></tr>
<tr><td>user_manager.html</td><td>用户管理</td></tr>
</tbody>
</table>

<h2>配置项</h2>
<table>
<thead><tr><th>配置键</th><th>说明</th><th>示例</th></tr></thead>
<tbody>
<tr><td>admin.port</td><td>GM 后台端口</td><td>8010</td></tr>
<tr><td>admin.user</td><td>登录用户名</td><td>admin</td></tr>
<tr><td>admin.password</td><td>登录密码</td><td>sunrise</td></tr>
<tr><td>admin.uipath</td><td>静态资源路径</td><td>admin-ui/</td></tr>
<tr><td>admin.jwt.expiration</td><td>JWT 过期时间（毫秒）</td><td>86400000</td></tr>
</tbody>
</table>
`);
