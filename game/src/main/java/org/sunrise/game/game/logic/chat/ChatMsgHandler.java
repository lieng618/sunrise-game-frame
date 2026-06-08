package org.sunrise.game.game.logic.chat;

import com.alibaba.fastjson2.JSON;
import com.github.houbb.sensitive.word.core.SensitiveWordHelper;
import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.gm.GmCommandManager;
import org.sunrise.game.game.logic.system.CdkSystem;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.modules.CdkModule;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.genProto.gen.ChatProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.HashMap;
import java.util.Map;

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

        RpcFunction.newInstance().call(CallEnum.GlobalChatService_chat, humanObject.getHumanId(), humanObject.getModule(DataModule.class).getName(), SensitiveWordHelper.replace(msg));
    }

    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_Horn_VALUE)
    public static void horn(HumanObject humanObject) {
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_Horn_VALUE);
    }

    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_CDK_VALUE)
    public static void useCdk(HumanObject humanObject, ChatProto.MC2S_CDK data) {
        String code = data.getCode();
        if (code.trim().isEmpty()) {
            humanObject.sendTips("兑换码无效");
            return;
        }
        code = code.trim();

        CdkModule cdkModule = humanObject.getModule(CdkModule.class);
        if (cdkModule == null) {
            humanObject.sendTips("兑换失败");
            return;
        }
        if (cdkModule.hasUsed(code)) {
            humanObject.sendTips("该兑换码已使用过");
            return;
        }

        CdkSystem cdkSystem = GameSystemUtils.getSystem(CdkSystem.class);
        if (cdkSystem == null) {
            humanObject.sendTips("兑换码无效");
            return;
        }

        CdkSystem.CdkInfo info = cdkSystem.getCdkInfo(code);
        if (info == null) {
            humanObject.sendTips("兑换码无效");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < info.getStartTime()) {
            humanObject.sendTips("兑换码尚未生效");
            return;
        }
        if (now >= info.getEndTime()) {
            humanObject.sendTips("兑换码已过期");
            return;
        }

        if (!cdkSystem.tryConsume(code)) {
            humanObject.sendTips("兑换码已被领完");
            return;
        }

        cdkModule.markUsed(code);

        Map<String, Object> redeemData = new HashMap<>();
        redeemData.put("code", code);
        RpcFunction.newInstance().call(CallEnum.GmBackRecvMessageService_recvMessage, "cdkRedeem", JSON.toJSONString(redeemData));

        String attachmentsJson = info.getAttachments() != null ? JSON.toJSONString(info.getAttachments()) : "[]";
        RpcFunction.newInstance().call(CallEnum.GlobalMailService_sendMail,
                humanObject.getHumanId(),
                info.getTemplateId(),
                attachmentsJson,
                "兑换码奖励");

        humanObject.sendTips("兑换成功");
    }

    @MsgHandlerMethod(packetId = ChatProto.FROM_CLIENT.C2S_GetHistory_VALUE)
    public static void history(HumanObject humanObject) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalChatService_history, humanObject.getHumanId());
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
