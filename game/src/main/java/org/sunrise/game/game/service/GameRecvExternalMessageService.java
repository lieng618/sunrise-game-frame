package org.sunrise.game.game.service;

import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.ProtoParserUtils;
import org.sunrise.game.game.login.LoginMsgHandler;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

@RpcService
public class GameRecvExternalMessageService extends BaseService {

    public GameRecvExternalMessageService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void init() {
        super.init();
    }

    @RpcMethod
    public void recvMessage(long connectionId, byte[] data, String externalNodeId) {
        try {
            var msg = TopicProto.MBasePacketData.parseFrom(data);
            var method = ProtoParserUtils.getProtoParserClass(msg.getPacketType().getNumber(), msg.getPacketId());
            Object msgData = null;
            if (method != null) {
                msgData = method.invoke(null, msg.getPacketData());
            }

            switch (msg.getPacketType()) {
                case TopicProto.TOPIC.TOPIC_TYPE_LOGIN: {
                    LogCore.GameServer.debug("recv msg, connectionId = {}, packetType = {}, packetId = {}, msgData = {{}}", connectionId, msg.getPacketType(), msg.getPacketId(), msgData == null ? "" : msgData.toString().replace("\n", ""));
                    LoginMsgHandler.handlerLogin(connectionId, msg.getPacketId(), msgData, externalNodeId);
                    break;
                }
                default:
                    String humanId = HumanObjectManger.humanIds.get(connectionId);
                    if (humanId == null) {
                        LogCore.GameServer.error("recv msg, connectionId = {}, packetType = {}, packetId = {}, humanObject not found", connectionId, msg.getPacketType(), msg.getPacketId());
                        return;
                    }
                    HumanObject humanObject = HumanObjectManger.getHumanObject(humanId);
                    if (humanObject == null) {
                        LogCore.GameServer.error("recv msg, humanId = {}, packetType = {}, packetId = {}, humanObject not found", humanId, msg.getPacketType(), msg.getPacketId());
                        return;
                    }
                    LogCore.GameServer.debug("recv msg, humanId = {}, packetType = {}, packetId = {}, msgData = {{}}", humanId, msg.getPacketType(), msg.getPacketId(), msgData == null ? "" : msgData.toString().replace("\n", ""));
                    // 登录后的消息，添加到玩家消息队列
                    humanObject.getMsgQueue().add(msg);
            }
        } catch (Exception e) {
            LogCore.GameServer.error("recv msg, Exception = {}", e.getLocalizedMessage());
        }
    }
}
