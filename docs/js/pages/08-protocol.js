registerPage('protocol', '协议规范', 'Protobuf 协议定义、命名规则、各模块协议一览', () => `
<h1>协议规范</h1>
<p class="page-desc">基于 Protobuf 的消息协议定义，包含命名规则、消息结构、TOPIC 枚举。框架通过命名约定自动反射解析协议类，必须严格遵循规则</p>

<h2>外层消息结构</h2>
<p>客户端与对外服服务器之间业务消息统一使用protobuf格式的消息，结构为 <code>TopicProto.MBasePacketData</code>：</p>
<pre><code class="language-protobuf">message MBasePacketData {
  TOPIC packet_type = 1; // 模块类型（对应 TOPIC 枚举）
  uint32 packet_id = 2;  // 模块内消息 ID（对应 FROM_CLIENT/FROM_SERVER 枚举）
  bytes packet_data = 3; // protobuf 消息体（具体业务消息的序列化）
}</code></pre>

<h2>网络包结构</h2>
<p>TCP/WebSocket/KCP 层传输的 SocketMessage：</p>
<pre><code class="language-java">public class SocketMessage {
    int messageType;
    byte[] data;
}

public class MessageType {
    /** 请求命令类型:心跳 */
    public static int idle = 0;
    /** 请求命令类型:业务 */
    public static int biz = 1;
}

public class SocketMessageEncoder extends MessageToByteEncoder<SocketMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, SocketMessage msg, ByteBuf out) throws Exception {
        out.writeInt(msg.getMessageType());
        out.writeInt(msg.getData().length);
        out.writeBytes(msg.getData());
    }
}
</code></pre>
<p>客户端与对外服服务器、各个rpc服务器之间通信的网络包的消息结构都为SocketMessage，只是data代表的结构不一样。</p>
<p>对外服会把要发给客户端的protobuf消息放入SocketMessage.data中（参考ExternalRecvGameMessageService.recvMessage()）。</p>
<p>各个rpc服务器之间通信，也就是RpcFunction.call()方法，会把Call对象转化为字节，放入SocketMessage.data中（参考BaseClientManager.sendMsgToServer()）。</p>
<p>最终在网络上传输时，会通过netty的编解码方法（包org.sunrise.game.core.coder），进行编解码。</p>
<p>四个字节写入消息类型（MessageType），心跳类型主要用于节点间的底层心跳通信，客户端与对外服的通信、所有节点间的rpc调用都为业务类型。</p>
<p>四个字节写入data的长度，之后在写入真正的业务数据。</p>

<pre><code class="language-java">public class ConnectObject {
    public void sendMsg(int packetType, int packetId) {
        var sendBuilder = TopicProto.MBasePacketData.newBuilder().setPacketType(TopicProto.TOPIC.forNumber(packetType)).setPacketId(packetId);
        RpcFunction.newInstance(externalNodeId).call(CallEnum.ExternalRecvGameMessageService_recvMessage, connectId, sendBuilder.build().toByteArray(), firstSend ? "" : RpcNodeManager.getRpcServerNodeId());
    }
}
</code></pre>
<p>游戏服在ConnectObject中，调用sendMsg() 发送protobuf消息，游戏服通过rpc调用，把protobuf数据作为参数传递给对外服。这些参数都会封装到Call对象中，最终放入SocketMessage进行网络传输。</p>

<pre><code class="language-java">@RpcService
public class ExternalRecvGameMessageService extends BaseService {
    @RpcMethod
    public void recvMessage(long connectionId, byte[] data) {
        if (connectionId > 0) {
            var connection = ExternalConnectionManager.getClientConnect(connectionId);
            if (connection != null) {
                connection.sendMessage(data);
            }
        }
    }
}
public class ClientConnection {
    public void sendMessage(byte[] data) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new SocketMessage(MessageType.biz, data));
        } else if (ukcp != null && ukcp.isActive()) {
            ByteBuf buf = Unpooled.buffer(4 + 4 + data.length);
            buf.writeInt(MessageType.biz);
            buf.writeInt(data.length);
            buf.writeBytes(data);
            ukcp.write(buf);
            buf.release();
        }
    }
}
</code></pre>
<p>在对外服ExternalRecvGameMessageService中，recvMessage()接收到数据。对外服接收到rpc后，把protobuf数据，通过ClientConnection.sendMessage()发给客户端。可以看到，最终的protobuf数据在对外服传递给客户端时，放入了SocketMessage中。
</p>
<p>以上是底层消息结构的完整解析，只编写业务逻辑的话，只需要参考其他模块，实现起来是比较简单的，但了解底层结构对框架的理解有很多帮助。</p>

<h2>TOPIC 模块枚举</h2>
<table>
<thead><tr><th>枚举值</th><th>数值</th><th>Proto 类</th><th>说明</th></tr></thead>
<tbody>
<tr><td>TOPIC_TYPE_LOGIN</td><td>0</td><td>LoginProto</td><td>登录/角色</td></tr>
<tr><td>TOPIC_TYPE_HUMAN</td><td>1</td><td>HumanProto</td><td>角色信息</td></tr>
<tr><td>TOPIC_TYPE_CHAT</td><td>2</td><td>ChatProto</td><td>聊天</td></tr>
<tr><td>TOPIC_TYPE_MAP</td><td>3</td><td>MapProto</td><td>地图</td></tr>
<tr><td>TOPIC_TYPE_MINER</td><td>4</td><td>MinerProto</td><td>挖矿</td></tr>
<tr><td>TOPIC_TYPE_ITEM</td><td>5</td><td>ItemProto</td><td>背包</td></tr>
<tr><td>TOPIC_TYPE_TASK</td><td>6</td><td>TaskProto</td><td>任务</td></tr>
<tr><td>TOPIC_TYPE_FRIEND</td><td>7</td><td>FriendProto</td><td>好友</td></tr>
<tr><td>TOPIC_TYPE_MAIL</td><td>8</td><td>MailProto</td><td>邮件</td></tr>
<tr><td>TOPIC_TYPE_ACTIVITY</td><td>9</td><td>ActivityProto</td><td>活动</td></tr>
<tr><td>TOPIC_TYPE_RANK</td><td>10</td><td>RankProto</td><td>排行榜</td></tr>
<tr><td>TOPIC_TYPE_DROP</td><td>11</td><td>DropProto</td><td>掉落</td></tr>
<tr><td>TOPIC_TYPE_BATTLE</td><td>12</td><td>BattleProto</td><td>战斗</td></tr>
</tbody>
</table>

<h2>公共包装消息</h2>
<p><code>define.proto</code> 提供嵌入嵌套 <code>oneof</code> 时使用的基础包装类型：<code>IntValue</code>、<code>IntValueList</code>、<code>LongValue</code>、<code>LongValueList</code>、<code>StringValue</code>、<code>StringValueList</code>、<code>BoolValue</code>、<code>BoolValueList</code>。它们没有独立 TOPIC/packetId，只作为字段值使用。</p>

<h2>命名规则</h2>
<div class="callout callout-danger">
    <p><strong>⚠️ 框架通过命名约定自动反射解析协议类，必须严格遵循以下规则！</strong></p>
</div>

<h3>文件命名</h3>
<table>
<thead><tr><th>项</th><th>规则</th><th>示例</th></tr></thead>
<tbody>
<tr><td>.proto 文件名</td><td>小写下划线</td><td>task.proto</td></tr>
<tr><td>Java 外部类名</td><td>XxxProto</td><td>TaskProto</td></tr>
<tr><td>TOPIC 枚举名</td><td>TOPIC_TYPE_XXX</td><td>TOPIC_TYPE_TASK</td></tr>
</tbody>
</table>

<h3>协议命名</h3>
<table>
<thead><tr><th>类型</th><th>命名格式</th><th>示例</th></tr></thead>
<tbody>
<tr><td>客户端枚举</td><td>FROM_CLIENT.C2S_Xxx</td><td>C2S_AcceptTask</td></tr>
<tr><td>服务端枚举</td><td>FROM_SERVER.S2C_Xxx</td><td>S2C_TaskUpdate</td></tr>
<tr><td>客户端消息</td><td>MC2S_Xxx</td><td>MC2S_AcceptTask</td></tr>
<tr><td>服务端消息</td><td>MS2C_Xxx</td><td>MS2C_TaskUpdate</td></tr>
</tbody>
</table>

<h3>自动反射映射规则</h3>
<pre><code class="language-text">TOPIC_TYPE_TASK → 提取 "TASK" → 首字母大写 + "Proto" → "TaskProto"
C2S_AcceptTask  → 加 "M" 前缀 → MC2S_AcceptTask（消息体类名）

ProtoParserUtils.init() 注册客户端请求解析器：
key = packetType * 100000 + packetId
value = XxxProto.MC2S_Xxx.parseFrom 方法

LogicUtils 的消息处理器路由使用：
key = packetType * 10000 + packetId</code></pre>

<h2>协议定义示例</h2>
<pre><code class="language-protobuf">syntax = "proto3";
option java_outer_classname = "TaskProto";
package org.sunrise.game.genProto.gen;

enum FROM_CLIENT {
  C2S_GetTaskList = 0;
  C2S_AcceptTask = 1;
  C2S_SubmitTask = 2;
}

enum FROM_SERVER {
  S2C_GetTaskList = 0;
  S2C_TaskUpdate = 1;
}

message MC2S_AcceptTask {
  uint32 taskId = 1;
}

message MS2C_GetTaskList {
  repeated MTaskInfo tasks = 1;
}

message MTaskInfo {
  uint32 taskId = 1;
  int32 status = 2;
  int32 progress = 3;
}</code></pre>

<h2>各模块协议一览</h2>
<h3>LoginProto（TOPIC_TYPE_LOGIN = 0）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_Login</td><td>uid, token</td><td>登录请求。<code>player.auth.enabled=false</code> 时用 uid；开启时用 HTTP /login 返回的 token</td></tr>
<tr><td>C→S</td><td>C2S_HumanList</td><td>（空）</td><td>请求角色列表</td></tr>
<tr><td>C→S</td><td>C2S_SelectHuman</td><td>pos, server_id</td><td>选择角色</td></tr>
<tr><td>C→S</td><td>C2S_ClientPing</td><td>time</td><td>客户端心跳（60秒超时）</td></tr>
<tr><td>S→C</td><td>S2C_Login</td><td>account_id</td><td>登录响应</td></tr>
<tr><td>S→C</td><td>S2C_Queue</td><td>pos, queues, need_time</td><td>排队位置、队列总人数、预计秒数</td></tr>
<tr><td>S→C</td><td>S2C_HumanList</td><td>human_list</td><td>角色列表</td></tr>
<tr><td>S→C</td><td>S2C_SelectHuman</td><td>（空）</td><td>选角响应</td></tr>
<tr><td>S→C</td><td>S2C_ClientPing</td><td>time</td><td>心跳回包</td></tr>
<tr><td>S→C</td><td>S2C_ServerInfo</td><td>server_time, time_zone, server_open_time</td><td>服务器时间信息</td></tr>
<tr><td>S→C</td><td>S2C_Kick</td><td>reason</td><td>被踢下线</td></tr>
</tbody>
</table>

<h3>HumanProto（TOPIC_TYPE_HUMAN = 1）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_ChangeName</td><td>name</td><td>修改角色名</td></tr>
<tr><td>C→S</td><td>C2S_ChangeHeadIcon</td><td>head_icon_id</td><td>修改头像</td></tr>
<tr><td>C→S</td><td>C2S_ChangeSex</td><td>sex</td><td>修改性别</td></tr>
<tr><td>S→C</td><td>S2C_HumanInfo</td><td>uid, name, human_id, level, server_id, exp, account_id, head_icon, fight_power, sex</td><td>登录角色基础数据</td></tr>
<tr><td>S→C</td><td>S2C_SendInfoEnd</td><td>（空）</td><td>所有模块初始数据发送完毕</td></tr>
<tr><td>S→C</td><td>S2C_HumanInfoChange</td><td>level, exp, fight_power</td><td>基础数值变化</td></tr>
<tr><td>S→C</td><td>S2C_ChangeName / ChangeHeadIcon / ChangeSex</td><td>与请求对应</td><td>修改成功回包</td></tr>
</tbody>
</table>

<h3>ItemProto（TOPIC_TYPE_ITEM = 5）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetItemList</td><td>（空）</td><td>请求背包列表</td></tr>
<tr><td>C→S</td><td>C2S_UseItem</td><td>itemId, count</td><td>使用物品</td></tr>
<tr><td>C→S</td><td>C2S_RemoveItem</td><td>index, count</td><td>按格子删除物品</td></tr>
<tr><td>C→S</td><td>C2S_RemoveItemById</td><td>itemId, count</td><td>按物品 ID 删除物品</td></tr>
<tr><td>C→S</td><td>C2S_SortItem</td><td>（空）</td><td>整理背包</td></tr>
<tr><td>C→S</td><td>C2S_SellItem</td><td>itemId, count</td><td>出售物品</td></tr>
<tr><td>S→C</td><td>S2C_ItemList</td><td>items, capacity</td><td>背包数据</td></tr>
<tr><td>S→C</td><td>S2C_ItemUpdate</td><td>items</td><td>发生变化的格子；itemId、count、index</td></tr>
</tbody>
</table>

<h3>FriendProto（TOPIC_TYPE_FRIEND = 7）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetFriendList</td><td>（空）</td><td>请求好友列表</td></tr>
<tr><td>C→S</td><td>C2S_SearchPlayer</td><td>keyword</td><td>搜索玩家；当前服务端示例把 keyword 当 humanId 查询</td></tr>
<tr><td>C→S</td><td>C2S_AddFriendRequest</td><td>targetHumanId</td><td>发送好友申请</td></tr>
<tr><td>C→S</td><td>C2S_HandleFriendRequest</td><td>applicantHumanId, action</td><td>处理好友申请</td></tr>
<tr><td>C→S</td><td>C2S_DeleteFriend</td><td>friendHumanId</td><td>删除好友</td></tr>
<tr><td>C→S</td><td>C2S_GetFriendRequestList</td><td>（空）</td><td>获取申请列表</td></tr>
<tr><td>C→S</td><td>C2S_GetFriendRecommendationList</td><td>（空）</td><td>获取在线玩家推荐</td></tr>
<tr><td>S→C</td><td>S2C_GetFriendList</td><td>friends</td><td>好友列表</td></tr>
<tr><td>S→C</td><td>S2C_SearchPlayer</td><td>players</td><td>搜索结果</td></tr>
<tr><td>S→C</td><td>S2C_FriendUpdate</td><td>friend, updateType</td><td>好友更新</td></tr>
<tr><td>S→C</td><td>S2C_FriendRequestUpdate</td><td>request</td><td>协议可携带申请；当前新增申请通知发送空 request，客户端应重新拉取申请列表</td></tr>
<tr><td>S→C</td><td>S2C_GetFriendRequestList</td><td>requests</td><td>申请列表</td></tr>
<tr><td>S→C</td><td>S2C_GetFriendRecommendationList</td><td>players</td><td>推荐好友</td></tr>
</tbody>
</table>

<h3>ChatProto（TOPIC_TYPE_CHAT = 2）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_Chat</td><td>msg</td><td>发送聊天</td></tr>
<tr><td>C→S</td><td>C2S_Horn</td><td>（空）</td><td>当前处理器回推空的 S2C_Horn，示例占位</td></tr>
<tr><td>C→S</td><td>C2S_GetHistory</td><td>count（请求消息体定义字段）</td><td>请求聊天历史</td></tr>
<tr><td>C→S</td><td>C2S_CDK</td><td>code</td><td>使用兑换码，成功/失败均 S2C_Tips 提示，成功发邮件</td></tr>
<tr><td>S→C</td><td>S2C_Chat</td><td>id, msg, time, name</td><td>聊天消息推送</td></tr>
<tr><td>S→C</td><td>S2C_Horn</td><td>（空）</td><td>喇叭消息，当前无独立消息体</td></tr>
<tr><td>S→C</td><td>S2C_History</td><td>history（MS2C_Chat 列表）</td><td>聊天历史</td></tr>
<tr><td>S→C</td><td>S2C_Tips</td><td>id, msg</td><td>Tips 提示</td></tr>
</tbody>
</table>

<h3>MailProto（TOPIC_TYPE_MAIL = 8）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetMailList</td><td>（空）</td><td>请求邮件列表</td></tr>
<tr><td>C→S</td><td>C2S_ReadMail</td><td>mailId</td><td>标记已读</td></tr>
<tr><td>C→S</td><td>C2S_ReceiveMailAttachment</td><td>mailId</td><td>领取附件</td></tr>
<tr><td>C→S</td><td>C2S_DeleteMail</td><td>mailId</td><td>删除邮件</td></tr>
<tr><td>S→C</td><td>S2C_GetMailList</td><td>mails</td><td>邮件列表</td></tr>
<tr><td>S→C</td><td>S2C_NewMail</td><td>mail</td><td>新邮件通知</td></tr>
<tr><td>S→C</td><td>S2C_MailUpdate</td><td>mailId, status</td><td>status：1 已读、2 已领取、3 已删除</td></tr>
</tbody>
</table>

<h3>RankProto（TOPIC_TYPE_RANK = 10）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetRankList</td><td>rank_type, page, page_size</td><td>页码从 1 开始；page_size 默认 20、最大 100</td></tr>
<tr><td>C→S</td><td>C2S_GetMyRank</td><td>rank_type</td><td>查询自己的名次</td></tr>
<tr><td>S→C</td><td>S2C_GetRankList</td><td>rank_type, entries, total_count</td><td>分页排行条目</td></tr>
<tr><td>S→C</td><td>S2C_GetMyRank</td><td>rank_type, rank, my_entry</td><td>未上榜 rank=0 且无 my_entry</td></tr>
</tbody>
</table>

<h3>TaskProto（TOPIC_TYPE_TASK = 6）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetTaskList</td><td>（空）</td><td>获取任务列表</td></tr>
<tr><td>C→S</td><td>C2S_AcceptTask</td><td>taskId</td><td>接受任务</td></tr>
<tr><td>C→S</td><td>C2S_SubmitTask</td><td>taskId</td><td>提交任务</td></tr>
<tr><td>S→C</td><td>S2C_GetTaskList</td><td>tasks</td><td>taskId、progress、status</td></tr>
<tr><td>S→C</td><td>S2C_TaskUpdate</td><td>task</td><td>单任务更新</td></tr>
</tbody>
</table>

<h3>MapProto（TOPIC_TYPE_MAP = 3）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_Enter</td><td>id</td><td>进入地图</td></tr>
<tr><td>C→S</td><td>C2S_Move</td><td>mapPostX/Y/Z, Orientation</td><td>上报位置与朝向</td></tr>
<tr><td>C→S</td><td>C2S_Leave</td><td>id</td><td>离开地图</td></tr>
<tr><td>S→C</td><td>S2C_SceneSync</td><td>units, positions, unit_attributes</td><td>进图全量同步</td></tr>
<tr><td>S→C</td><td>S2C_UnitEnter / Leave</td><td>unit / unit_id</td><td>单位进出地图广播</td></tr>
<tr><td>S→C</td><td>S2C_UnitPositionUpdate</td><td>position</td><td>单位位置变化</td></tr>
<tr><td>S→C</td><td>S2C_UnitAttributeUpdate</td><td>attributes</td><td>单位属性变化</td></tr>
<tr><td>S→C</td><td>S2C_LastMap</td><td>id</td><td>登录时下发的上次地图</td></tr>
</tbody>
</table>

<h3>MinerProto（TOPIC_TYPE_MINER = 4）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_SyncData</td><td>level_index, upgrade_points, stats, upgrade_counts</td><td>客户端同步矿场状态</td></tr>
<tr><td>C→S</td><td>C2S_RankList</td><td>（空）</td><td>请求矿场排行</td></tr>
<tr><td>S→C</td><td>S2C_SyncData</td><td>与同步数据结构一致</td><td>登录/数据同步时下发</td></tr>
<tr><td>S→C</td><td>S2C_RankList</td><td>infos(name, level_index)</td><td>矿场排行</td></tr>
</tbody>
</table>

<h3>ActivityProto（TOPIC_TYPE_ACTIVITY = 9）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_GetActivityList</td><td>（空）</td><td>获取活动列表</td></tr>
<tr><td>C→S</td><td>C2S_ActivityAction</td><td>id, action_id, data</td><td>活动行为，data 由活动逻辑解析</td></tr>
<tr><td>S→C</td><td>S2C_GetActivityList</td><td>activities(id, status, beginTime, endTime)</td><td>活动列表</td></tr>
<tr><td>S→C</td><td>S2C_ActivityUpdate</td><td>activity</td><td>活动状态推送</td></tr>
<tr><td>S→C</td><td>S2S_ActivityAction</td><td>id, action_id, data</td><td>现存枚举拼写；当前消息类名为 MS2C_ActivityAction，实际代码未见发送路径</td></tr>
</tbody>
</table>

<h3>BattleProto（TOPIC_TYPE_BATTLE = 12）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_Attack</td><td>defender_unit_id</td><td>攻击目标</td></tr>
<tr><td>S→C</td><td>S2C_Attack</td><td>attack_unit_id, defender_unit_id</td><td>攻击广播</td></tr>
<tr><td>S→C</td><td>S2C_Damage</td><td>attack_unit_id, defender_unit_id, damage</td><td>伤害结果</td></tr>
</tbody>
</table>

<h3>DropProto（TOPIC_TYPE_DROP = 11）</h3>
<table>
<thead><tr><th>方向</th><th>消息</th><th>字段</th><th>说明</th></tr></thead>
<tbody>
<tr><td>C→S</td><td>C2S_PickupDrop</td><td>unit_id</td><td>拾取地图中的掉落物 unitId</td></tr>
<tr><td>S→C</td><td>S2C_DropPickupResult</td><td>unit_id, success</td><td>拾取结果；成功时另发 MAP.UnitLeave 与 ITEM.ItemUpdate</td></tr>
</tbody>
</table>
`);
