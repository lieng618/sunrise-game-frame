package org.sunrise.game.core.server;

import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.graceful.OnShutdown;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseServerManager {
    private static final Map<String, BaseServer> baseServers = new ConcurrentHashMap<>();

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
                Utils.sleep(1);
                baseServer = new BaseServer();
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
            var nodeConnect = ConnectionManager.getConnect(message.getToNodeId());
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
     * 停机时关闭所有已注册的 BaseServer。
     */
    @OnShutdown(order = 90)
    public static void shutdownAll() {
        for (Map.Entry<String, BaseServer> entry : baseServers.entrySet()) {
            try {
                entry.getValue().onStop();
            } catch (Exception e) {
                LogCore.BaseServer.error("BaseServerManager shutdownAll error, nodeId={}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
        baseServers.clear();
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
