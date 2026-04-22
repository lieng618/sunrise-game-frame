package org.sunrise.game.game.service;

import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.genProto.gen.ChatProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

@RpcService
public class ChatRpcListenService extends BaseService {
    public ChatRpcListenService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void onChat(String humanId, String message, long time) {
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_Chat_VALUE, ChatProto.MS2C_Chat.newBuilder().setId(humanId).setMsg(message).setTime(time));
        }
    }
}