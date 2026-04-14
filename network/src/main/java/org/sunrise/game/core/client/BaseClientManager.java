package org.sunrise.game.core.client;

import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.SocketMessage;

import java.util.HashMap;
import java.util.Map;

public class BaseClientManager {
    private static final Map<String, BaseClient> baseClients = new HashMap<>();

    /**
     * 使用new BaseClient(),创建的客户端,需手动注册到管理器中
     */
    public static void register(BaseClient baseClient) {
        baseClients.putIfAbsent(baseClient.getNodeId(), baseClient);
    }

    /**
     * 移除
     */
    public static void remove(String nodeId) {
        baseClients.remove(nodeId);
    }

    /**
     * 创建默认客户端
     */
    public static BaseClient createBaseClient() {
        return createCore();
    }

    /**
     * 创建默认客户端
     */
    public static BaseClient createBaseClient(String nodeId) {
        BaseClient baseClient;
        if (baseClients.get(nodeId) != null) {
            baseClient = createCore();
        } else {
            baseClient = new BaseClient(nodeId);
            baseClients.put(baseClient.getNodeId(), baseClient);
        }
        return baseClient;
    }

    private static BaseClient createCore() {
        BaseClient baseClient = new BaseClient();
        while (true) {
            if (baseClients.containsKey(baseClient.getNodeId())) {
                try {
                    Thread.sleep(5);
                    baseClient = new BaseClient();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                break;
            }
        }
        baseClients.put(baseClient.getNodeId(), baseClient);
        return baseClient;
    }

    public static BaseClient getBaseClient(String nodeId) {
        return baseClients.get(nodeId);
    }

    /**
     * 放入发送队列
     */
    public static void sendToServer(BaseMessage message) {
        BaseClient baseClient = baseClients.get(message.getNodeId());
        if (baseClient != null) {
            baseClient.getMessageManager().sendMsg(message);
        }
    }

    /**
     * 最终发给服务器的方法
     */
    public static void sendMsgToServer(BaseMessage message) {
        BaseClient baseClient = baseClients.get(message.getNodeId());
        if (baseClient != null) {
            if (baseClient.getServerChannel() == null) {
                LogCore.BaseClient.warn("server disconnect, discard message, NodeId = { {} }, MessageId = { {} }", message.getNodeId(), message.getMessageId());
            } else {
                baseClient.getServerChannel().writeAndFlush(new SocketMessage(MessageType.biz, MessageUtils.toBytes(message)));
            }
        }
    }

    /**
     * 放入接收队列
     */
    public static void recvFromServer(String nodeId, Object data) {
        BaseClient baseClient = baseClients.get(nodeId);
        if (baseClient != null) {
            baseClient.getMessageManager().recvMsg(data);
        }
    }
}
