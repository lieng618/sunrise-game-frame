package org.sunrise.game.core.server;

import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.SocketMessage;

import java.util.HashMap;
import java.util.Map;

public class BaseServerManager {
    private static final Map<String, BaseServer> baseServers = new HashMap<>();

    /**
     * 使用new BaseServer(),创建的服务器,需手动注册到管理器中
     */
    public static void register(BaseServer baseServer) {
        baseServers.putIfAbsent(baseServer.getNodeId(), baseServer);
    }

    /**
     * 创建默认服务器
     */
    public static BaseServer createBaseServer() {
        return createCore();
    }

    /**
     * 创建默认服务器
     * nodeId已存在时,使用默认的nodeId
     */
    public static BaseServer createBaseServer(String nodeId) {
        BaseServer baseServer;
        if (baseServers.get(nodeId) != null) {
            baseServer = createCore();
        } else {
            baseServer = new BaseServer(nodeId);
            baseServers.put(baseServer.getNodeId(), baseServer);
        }
        return baseServer;
    }

    private static BaseServer createCore() {
        BaseServer baseServer = new BaseServer();
        while (true) {
            if (baseServers.containsKey(baseServer.getNodeId())) {
                try {
                    Thread.sleep(1);
                    baseServer = new BaseServer();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                break;
            }
        }
        baseServers.put(baseServer.getNodeId(), baseServer);
        return baseServer;
    }

    /**
     * 放入发送队列
     * 必须指定:nodeId(发送方)
     * 发给客户端需指定:toNodeId(接收方)
     */
    public static void sendToClient(BaseMessage message) {
        BaseServer baseServer = baseServers.get(message.getNodeId());
        if (baseServer != null) {
            baseServer.getMessageManager().sendMsg(message);
        } else {
            LogCore.BaseServer.warn("BaseServer sendToClient fail, use NodeId = { {} }, message = {{}}", message.getNodeId(), message);
        }
    }

    /**
     * 最终发给client的方法
     */
    public static void sendMsgToClient(BaseMessage message) {
        BaseServer baseServer = baseServers.get(message.getNodeId());
        if (baseServer != null) {
            var nodeConnect = ConnectionManger.getConnect(message.getToNodeId());
            if (nodeConnect != null) {
                if (nodeConnect.getChannel() == null) {
                    LogCore.BaseServer.warn("client disconnect, discard message, cur NodeId = {}, to NodeId = {}, MessageId = {}", message.getNodeId(), message.getToNodeId(), message.getMessageId());
                } else {
                    nodeConnect.getChannel().writeAndFlush(new SocketMessage(MessageType.biz, MessageUtils.toBytes(message)));
                }
            }
        } else {
            LogCore.BaseServer.warn("BaseServer sendToClient fail, use NodeId = { {} }, message = {{}}", message.getNodeId(), message);
        }
    }

    /**
     * 放入接收队列
     */
    public static void recvFromClient(String nodeId, Object data) {
        BaseServer baseServer = baseServers.get(nodeId);
        if (baseServer != null) {
            baseServer.getMessageManager().recvMsg(data);
        } else {
            LogCore.BaseServer.warn("BaseServer recvFromClient fail, use NodeId = { {} }, message = {{}}", nodeId, data);
        }
    }
}
