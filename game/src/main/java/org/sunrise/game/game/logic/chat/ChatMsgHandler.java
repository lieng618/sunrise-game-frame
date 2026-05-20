package org.sunrise.game.game.logic.chat;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.gm.GmCommandManager;
import org.sunrise.game.genProto.gen.ChatProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE)
public class ChatMsgHandler {
    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_Chat_VALUE)
    public static void chat(HumanObject humanObject, ChatProto.MC2S_Chat data) {
        if (HumanObjectManger.muteHumanQueue.contains(humanObject.getHumanId())) {
            return;
        }

        String msg = data.getMsg();
        if (msg.startsWith(".")) {
            GmCommandManager.handleGmCommand(humanObject, msg);
            return;
        }

        RpcFunction.newInstance().call(CallEnum.GlobalChatService_chat, "humanId", humanObject.getHumanId(), "message", msg);
    }

    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_Horn_VALUE)
    public static void horn(HumanObject humanObject) {
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_Horn_VALUE);
    }

    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_GetHistory_VALUE)
    public static void history(HumanObject humanObject) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalChatService_history, "humanId", humanObject.getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            String humanId = (String) rpcResult.getContext("humanId");
            HumanObject humanObj = HumanObjectManger.getHumanObject(humanId);
            if (humanObj == null) return;
            if (rpcResult.getResult() != ErrorType.SUCCESS) return;
            byte[] protoData = (byte[]) rpcResult.getData("info");
            humanObj.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_History_VALUE, protoData);
        }, "humanId", humanObject.getHumanId());
    }
}
