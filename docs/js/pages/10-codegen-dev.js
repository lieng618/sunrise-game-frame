registerPage('code-gen', '代码生成', 'Proto、RPC 枚举、DB 实体、Luban 配置表', () => `
<h1>代码生成</h1>
<p class="page-desc">gen 模块提供协议、RPC 枚举、数据库实体的自动生成能力，减少手写代码错误</p>

<h2>协议生成</h2>
<h3>Proto 文件位置</h3>
<pre><code class="language-text">gen/src/main/java/org/sunrise/game/genProto/proto/
├── packet.proto       # TOPIC 枚举定义
├── login.proto        # 登录协议
├── human.proto        # 角色协议
├── chat.proto         # 聊天协议
├── map.proto          # 地图协议
├── miner.proto        # 挖矿协议
├── item.proto         # 背包协议
├── task.proto         # 任务协议
├── friend.proto       # 好友协议
├── mail.proto         # 邮件协议
├── activity.proto     # 活动协议
└── attribute.proto    # 属性协议</code></pre>

<h3>生成步骤</h3>
<ol class="step-list">
    <li>在 proto 目录下新建或修改 .proto 文件（遵循命名规则）</li>
    <li>在 packet.proto 中注册新的 TOPIC 枚举值</li>
    <li>执行 <code>gen.bat</code> 生成 Java 协议类</li>
</ol>
<pre><code class="language-bash">cd gen/src/main/java/org/sunrise/game/genProto
gen.bat</code></pre>
<p>生成后的 Java 类位于 <code>gen/src/main/java/org/sunrise/game/genProto/gen/</code>。</p>

<h2>RPC 枚举生成</h2>
<h3>运行方式</h3>
<p>运行主类 <code>org.sunrise.game.genRpc.GenRpcStartUp</code></p>

<h3>生成逻辑</h3>
<ol class="step-list">
    <li>扫描所有 <code>@RpcService</code> 类</li>
    <li>提取所有 <code>@RpcMethod</code> 方法</li>
    <li>按 <code>类名_方法名</code> 格式生成常量</li>
    <li>输出到 <code>CallEnum.java</code></li>
</ol>

<h3>生成的 CallEnum 示例</h3>
<pre><code class="language-java">public class CallEnum {
    public static final int ChatRpcListenService_onChat = 1;
    public static final int GlobalChatService_chat = 2;
    public static final int GlobalChatService_history = 3;
    public static final int ExternalRecvGameMessageService_recvMessage = 4;
    public static final int FriendRpcListenService_onNewFriendRequest = 5;
    public static final int FriendRpcListenService_onFriendAdded = 6;
    public static final int FriendRpcListenService_onFriendDeleted = 7;
    public static final int GlobalFriendService_handleFriendRequest = 8;
    public static final int GlobalFriendService_getFriends = 9;
    public static final int GlobalFriendService_sendFriendRequest = 10;
    public static final int GlobalFriendService_deleteFriend = 11;
    public static final int GlobalFriendService_getFriendRequests = 12;
    public static final int GameRecvGmBackService_recvMessage = 13;
    public static final int GameRecvMessageService_recvMessage = 14;
    public static final int GmBackRecvMessageService_recvMessage = 15;
    public static final int HttpRecvMessageService_updateExternalRemoteData = 16;
    public static final int HttpRecvMessageService_setExternalServerStatus = 17;
    public static final int HttpRecvMessageService_setWhitelist = 18;
    public static final int MailRpcListenService_onNewMail = 19;
    public static final int GlobalMailService_claimAttachment = 20;
    public static final int GlobalMailService_sendMail = 21;
    public static final int GlobalMailService_readMail = 22;
    public static final int GlobalMailService_deleteMail = 23;
    public static final int GlobalMailService_sendGroupMail = 24;
    public static final int GlobalMailService_sendAllMail = 25;
    public static final int GlobalMailService_getMailList = 26;
    public static final int GlobalPlayerInfoService_getPlayerInfo = 27;
    public static final int GlobalPlayerInfoService_updatePlayerInfo = 28;
    public static final int GlobalPlayerInfoService_getPlayerInfos = 29;
    public static final int GlobalPlayerInfoService_getAllHumanIds = 30;
}</code></pre>

<h2>数据库实体生成</h2>
<h3>运行方式</h3>
<p>运行主类 <code>org.sunrise.game.genDb.GenDbStartUp</code></p>

<h3>生成逻辑</h3>
<ol class="step-list">
    <li>连接数据库，读取表结构</li>
    <li>自动生成 Lombok @Data 实体类</li>
    <li>输出到 <code>gen/src/main/java/org/sunrise/game/genDb/gen/</code></li>
</ol>

<h2>配置表生成（Luban）</h2>
<h3>Excel 配置表位置</h3>
<pre><code class="language-text">tables/Datas/
├── __beans__.xlsx     # Bean 定义
├── __enums__.xlsx     # 枚举定义
├── __tables__.xlsx    # 表定义
├── activity.xlsx      # 活动配置
├── item.xlsx          # 物品配置
├── map.xlsx           # 地图配置
├── param.xlsx         # 参数配置（背包容量等）
└── task.xlsx          # 任务配置</code></pre>

<h3>生成步骤</h3>
<pre><code class="language-bash">cd tables
gen.bat</code></pre>
<p>生成后的 JSON 配置位于 <code>tables/json/</code>，Java 配置类位于 <code>game/.../config/Tables.java</code>。</p>

<h3>运行时读取</h3>
<pre><code class="language-java">// 获取单个配置
TbItem item = Tables.ConfigItem.get(itemId);
TbTask task = Tables.ConfigTask.get(taskId);
int capacity = Tables.ConfigParam.getItemBoxCapacity();

// 遍历配置
Tables.ConfigItem.getDataList().forEach(item -> {
    // 处理每个物品配置
});</code></pre>

<h3>热更配置</h3>
<p>GM 后台"配置更新"会广播 <code>reloadConfig</code> 指令，Game 服收到后执行 <code>ConfigUtils.load()</code> 重新加载配置。新增功能如果走 Tables，天然支持热更。</p>
`);

registerPage('development', '开发指南', '新增业务模块的完整开发流程与常见坑', () => `
<h1>开发指南</h1>
<p class="page-desc">如何在 game 模块内新增一个完整业务功能的完整流程</p>

<h2>功能分层</h2>
<p>一个新功能通常拆成 5 层：</p>
<table>
<thead><tr><th>层</th><th>说明</th><th>所在位置</th></tr></thead>
<tbody>
<tr><td>协议层</td><td>.proto 文件定义</td><td>gen/src/.../genProto/proto/</td></tr>
<tr><td>路由层</td><td>MsgHandler 消息处理器</td><td>game/src/.../game/logic/</td></tr>
<tr><td>玩家模块层</td><td>Module 数据与逻辑</td><td>game/src/.../game/modules/</td></tr>
<tr><td>跨服服务层</td><td>GlobalService / RpcListenService（如需要）</td><td>game/src/.../global/service/ + game/src/.../game/service/</td></tr>
<tr><td>配置表/系统层</td><td>Tables / BaseSystem（如需要）</td><td>tables/Datas/ + game/src/.../game/logic/system/</td></tr>
</tbody>
</table>

<h2>功能类型判断</h2>
<h3>纯玩家本地模块</h3>
<p>如背包扩展、任务字段扩展，只需：协议 + MsgHandler + Module + 数据存档。不需要跨服。</p>

<h3>跨服共享模块</h3>
<p>如聊天、好友、邮件，需要：game 服模块/handler + global 服 @RpcService + game 服监听回调服务（RpcListenService）。</p>

<h3>系统型功能</h3>
<p>如活动、重置逻辑、排行，需要：GameSystem 级系统 + 配置表 + 玩家模块 + 消息处理器。</p>

<h2>标准开发步骤（12步）</h2>
<ol class="step-list">
    <li><strong>设计协议</strong> - 新建 .proto 文件，定义 FROM_CLIENT / FROM_SERVER 枚举和消息体</li>
    <li><strong>注册 TOPIC</strong> - 在 packet.proto 中注册新的 TOPIC_TYPE_XXX</li>
    <li><strong>生成协议代码</strong> - 执行 gen.bat 生成 Java 协议类</li>
    <li><strong>创建玩家模块</strong> - 新建 XxxModule extends BaseModule，实现 init/load/save/sendToClient</li>
    <li><strong>注册模块</strong> - 在 HumanObject.createModules() 中注册新模块，@HumanModule注解自动注册</li>
    <li><strong>创建消息处理器</strong> - 新建 XxxMsgHandler，使用 @MsgHandlerClass + @MsgHandlerMethod</li>
    <li><strong>跨服逻辑</strong>（如需）- 新增 global @RpcService</li>
    <li><strong>回推需求</strong>（如需）- 新增 game RpcListenService</li>
    <li><strong>配置表</strong>（如需）- 补充 Excel / Luban / Config 读取</li>
    <li><strong>接入系统层</strong>（如需）- 新增 BaseSystem 子类，注册到 GameSystem，@GameSystem注解自动注册</li>
    <li><strong>客户端适配</strong> - sunrise-client 中添加 Handler</li>
    <li><strong>编译联调</strong> - mvn clean package，启动服务，用 sunrise-client 测试</li>
</ol>

<h2>模块开发详解</h2>
<h3>创建 Module</h3>
<pre><code class="language-java">@Getter
@Setter
public class AchievementModule extends BaseModule {
    private Map&lt;Integer, Integer&gt; progress = new HashMap&lt;&gt;();

    public AchievementModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        progress = new HashMap&lt;&gt;();
    }

    @Override
    public void load() {
        getDbData("progress", new TypeReference&lt;Map&lt;Integer, Integer&gt;&gt;() {}, value -> {
            if (value != null) {
                progress = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("progress", progress);
    }

    @Override
    public void sendToClient() {
        // 构建并发送 protobuf 消息
        AchievementProto.MS2C_AchievementList.Builder builder =
            AchievementProto.MS2C_AchievementList.newBuilder();
        progress.forEach((id, val) -> {
            builder.addAchievements(
                AchievementProto.MAchievementInfo.newBuilder()
                    .setId(id).setProgress(val).build());
        });
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ACHIEVEMENT_VALUE,
            AchievementProto.FROM_SERVER.S2C_AchievementList_VALUE,
            builder.build().toByteString());
    }

    public void addProgress(int achievementId, int value) {
        progress.merge(achievementId, value, Integer::sum);
    }
}</code></pre>

<h3>注册到 HumanObject</h3>
<pre><code class="language-java">// HumanObject.createModules() 中添加
modules.put(AchievementModule.class.getSimpleName(),
    new AchievementModule(humanId));</code></pre>

<h3>创建消息处理器</h3>
<pre><code class="language-java">@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_ACHIEVEMENT_VALUE)
public class AchievementMsgHandler {

    @MsgHandlerMethod(packetId = AchievementProto.FROM_CLIENT.C2S_GetList_VALUE)
    public static void getList(HumanObject humanObject) {
        humanObject.getModule(AchievementModule.class).sendToClient();
    }

    @MsgHandlerMethod(packetId = AchievementProto.FROM_CLIENT.C2S_Claim_VALUE)
    public static void claim(HumanObject humanObject, AchievementProto.MC2S_Claim data) {
        humanObject.getModule(AchievementModule.class).claim(data.getId());
    }
}</code></pre>

<h3>跨服服务</h3>
<pre><code class="language-java">@RpcService
public class AchievementService extends BaseService {
    public AchievementService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void claimReward(String humanId, int achievementId) {
        // 处理跨服共享逻辑
        returns("success", true);
    }
}</code></pre>

<h3>Game 侧监听服务</h3>
<pre><code class="language-java">@RpcService
public class AchievementRpcListenService extends BaseService {
    public AchievementRpcListenService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void onAchievementUpdate(String humanId, byte[] protoData) {
        HumanObject human = HumanObjectManger.getHumanObject(humanId);
        if (human != null) {
            human.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ACHIEVEMENT_VALUE,
                AchievementProto.FROM_SERVER.S2C_Update_VALUE, protoData);
        }
    }
}</code></pre>

<h2>常见坑</h2>
<div class="callout callout-danger">
    <p><strong>❌ 忘记在 packet.proto 注册 TOPIC</strong>：ProtoParserUtils 注册不到，客户端或服务端收不到对应协议</p>
</div>
<div class="callout callout-danger">
    <p><strong>❌ proto 命名不符合规则</strong>：ProtoParserUtils 或 MessageUtil 反射失败，自动注册失败。必须严格遵循 C2S_/S2C_/MC2S_/MS2C_ 前缀规则</p>
</div>
<div class="callout callout-danger">
    <p><strong>❌ 忘记加注解</strong>：玩家模块、系统层，消息处理函数漏加注解</p>
</div>
<div class="callout callout-warn">
    <p><strong>⚠️ sendToClient() 漏发登录同步数据</strong>：服务端有数据，客户端登录后界面空白</p>
</div>
<div class="callout callout-warn">
    <p><strong>⚠️ save() 漏写字段</strong>：在线逻辑正常，重新登录后数据丢失</p>
</div>
<div class="callout callout-warn">
    <p><strong>⚠️ load() 没处理老数据兼容</strong>：老号升级后空指针，新增字段默认值异常。建议 load 时对 null 做兜底</p>
</div>
<div class="callout callout-warn">
    <p><strong>⚠️ RPC 参数顺序写错</strong>：方法能调到，但数据乱位，返回结果异常。参数顺序必须和目标方法参数顺序一致</p>
</div>
`);
