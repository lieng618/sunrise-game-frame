package org.sunrise.game.game.human;

import com.google.protobuf.Message;
import com.google.protobuf.UnsafeByteOperations;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.db.entity.EntityHumanList;
import org.sunrise.game.genProto.gen.HumanProto;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.List;

@Getter
@Setter
public class ConnectObject {
    private final long connectId;
    private final int externalId; // 对外服id
    private final String uid;
    private int accountId;
    // 是否为首次向对外服发消息
    // 首次发送时，需要带上game服的rpc节点id，对外服后续发消息，就可以指定此game节点接收
    private boolean firstSend;
    // 记录此玩家当前在哪个对外服
    // rpc调用，就可以指定此对外服节点接收
    private String externalNodeId;

    public ConnectObject(long connectId, String uid, String externalNodeId) {
        this.connectId = connectId;
        this.uid = uid;
        this.externalId = (int) (connectId / Utils.ID_BASE_NUM);
        this.externalNodeId = externalNodeId;
    }

    /**
     * 向客户端发包静态方法
     */
    public static void sendToClient(long connectId, String externalNodeId,
                                    int packetType, int packetId, Message.Builder builder) {
        var sendBuilder = TopicProto.MBasePacketData.newBuilder()
                .setPacketType(TopicProto.TOPIC.forNumber(packetType))
                .setPacketId(packetId);
        if (builder != null) {
            sendBuilder.setPacketData(builder.build().toByteString());
        }
        RpcFunction.newInstance(externalNodeId).call(
                CallEnum.ExternalRecvGameMessageService_recvMessage,
                "id", connectId,
                "data", sendBuilder.build().toByteArray(),
                "nodeId", RpcNodeManager.getRpcServerNodeId());
        LogCore.GameServer.debug("sendToClient, connectionId = {}, packetType = {}, packetId = {}, msgData = {{}}",
                connectId, packetType, packetId, builder == null ? "" : builder.toString().replace("\n", ""));
    }

    public void sendMsg(int packetType, int packetId) {
        var sendBuilder = TopicProto.MBasePacketData.newBuilder().setPacketType(TopicProto.TOPIC.forNumber(packetType)).setPacketId(packetId);

        RpcFunction.newInstance(externalNodeId).call(CallEnum.ExternalRecvGameMessageService_recvMessage, "id", connectId, "data", sendBuilder.build().toByteArray(), "nodeId", firstSend ? "" : RpcNodeManager.getRpcServerNodeId());
        firstSend = true;

        LogCore.GameServer.debug("send msg, connectionId = {}, uid = {}, packetType = {}, packetId = {}", connectId, uid, packetType, packetId);
    }

    public void sendMsg(int packetType, int packetId, Message.Builder builder) {
        var sendBuilder = TopicProto.MBasePacketData.newBuilder().setPacketType(TopicProto.TOPIC.forNumber(packetType)).setPacketId(packetId);
        if (builder != null) {
            sendBuilder.setPacketData(builder.build().toByteString());
        }
        RpcFunction.newInstance(externalNodeId).call(CallEnum.ExternalRecvGameMessageService_recvMessage, "id", connectId, "data", sendBuilder.build().toByteArray(), "nodeId", firstSend ? "" : RpcNodeManager.getRpcServerNodeId());
        firstSend = true;

        LogCore.GameServer.debug("send msg, connectionId = {}, uid = {}, packetType = {}, packetId = {}, msgData = {{}}", connectId, uid, packetType, packetId, builder == null ? "" : builder.toString().replace("\n", ""));
    }

    public void sendMsg(int packetType, int packetId, byte[] rawData) {
        var sendBuilder = TopicProto.MBasePacketData.newBuilder().setPacketType(TopicProto.TOPIC.forNumber(packetType)).setPacketId(packetId);
        if (rawData != null) {
            sendBuilder.setPacketData(UnsafeByteOperations.unsafeWrap(rawData));
        }
        RpcFunction.newInstance(externalNodeId).call(CallEnum.ExternalRecvGameMessageService_recvMessage, "id", connectId, "data", sendBuilder.build().toByteArray(), "nodeId", firstSend ? "" : RpcNodeManager.getRpcServerNodeId());
        firstSend = true;

        LogCore.GameServer.debug("send msg, connectionId = {}, uid = {}, packetType = {}, packetId = {}", connectId, uid, packetType, packetId);
    }

    public void onLoadAccount(long accountId) {
        this.accountId = (int) accountId;
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_Login_VALUE, LoginProto.MS2C_Login.newBuilder().setAccountId(accountId));
    }

    public void onLoadHumanList(List<EntityHumanList> humanLists) {
        LoginProto.MS2C_HumanList.Builder builder = LoginProto.MS2C_HumanList.newBuilder();
        for (EntityHumanList entityInfo : humanLists) {
            LoginProto.STHumanShowInfo.Builder humanBuilder = LoginProto.STHumanShowInfo.newBuilder();
            humanBuilder.setHumanId(entityInfo.getHumanId());
            humanBuilder.setName(entityInfo.getName());
            humanBuilder.setLevel(entityInfo.getLevel());
            humanBuilder.setPos(entityInfo.getPos());
            humanBuilder.setServerId(entityInfo.getServerId());

            builder.addHumanList(humanBuilder);
        }
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_HumanList_VALUE, builder);
    }

    public void onSelectHuman() {
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_SelectHuman_VALUE);
        LogCore.GameServer.info("uid = { {} }, login success", uid);
    }

    public void onSendHumanData(HumanProto.MS2C_HumanInfo.Builder builder) {
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE, HumanProto.FROM_SERVER.S2C_HumanInfo_VALUE, builder);
    }

    public void onSendHumanDataEnd() {
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE, HumanProto.FROM_SERVER.S2C_SendInfoEnd_VALUE);
    }

    public void kick(String reason) {
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_Kick_VALUE, LoginProto.MS2C_Kick.newBuilder().setReason(reason));
        LogCore.GameServer.info("uid = { {} }, kick, reason = { {} }", uid, reason);
    }
}
