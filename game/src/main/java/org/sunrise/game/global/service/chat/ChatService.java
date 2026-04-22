package org.sunrise.game.global.service.chat;

import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.genProto.gen.ChatProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.service.BaseService;

import java.util.LinkedList;
import java.util.List;

/**
 * 跨服聊天系统
 */
@RpcService
public class ChatService extends BaseService {
    // 定义最大聊天记录数量
    private static final int MAX_MESSAGES = 50;
    private List<ChatData> messages = new LinkedList<>();

    public ChatService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void load() {
        getDbData("messages", new TypeReference<List<ChatData>>() {
        }, value -> {
            if (value != null) {
                messages = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("messages", messages);
    }

    @RpcMethod
    public void chat(String humanId, String message) {
        long time = System.currentTimeMillis();
        messages.add(new ChatData(humanId, time, message));

        if (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }

        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.ChatRpcListenService_onChat, "humanId", humanId, "message", message, "time", time);
    }

    @RpcMethod
    public void history(String humanId) {
        ChatProto.MS2C_History.Builder historyBuilder = ChatProto.MS2C_History.newBuilder();
        for (ChatData message : messages) {
            ChatProto.MS2C_Chat.Builder chatBuilder = ChatProto.MS2C_Chat.newBuilder();
            chatBuilder.setId(message.getHumanId());
            chatBuilder.setMsg(message.getMessage());
            chatBuilder.setTime(message.getSendTime());
            historyBuilder.addHistory(chatBuilder);
        }

        byte[] data = historyBuilder.build().toByteArray();
        returns("humanId", humanId, "info", data);
    }
}
