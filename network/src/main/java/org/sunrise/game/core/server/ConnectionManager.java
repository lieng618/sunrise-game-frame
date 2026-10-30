package org.sunrise.game.core.server;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private static final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    /**
     * 原子注册连接。只有成功占用该 nodeId 的连接才返回 true。
     */
    public static boolean createConnect(String connectId, Channel channel) {
        return connections.putIfAbsent(connectId, new Connection(connectId, channel)) == null;
    }

    /**
     * 仅当 nodeId 当前仍绑定到该 Channel 时才移除，避免重复连接关闭时误删原连接。
     */
    public static boolean removeConnect(String connectId, Channel channel) {
        Connection connection = connections.get(connectId);
        if (connection == null || connection.getChannel() != channel) {
            return false;
        }
        if (!connections.remove(connectId, connection)) {
            return false;
        }
        channel.close();
        return true;
    }

    public static Connection getConnect(String connectId) {
        return connections.get(connectId);
    }

    public static boolean isConnectExist(String connectId) {
        return connections.containsKey(connectId);
    }
}
