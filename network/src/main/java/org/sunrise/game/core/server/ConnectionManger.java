package org.sunrise.game.core.server;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManger {
    private static final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    public static void createConnect(String connectId, Channel channel) {
        connections.put(connectId, new Connection(connectId, channel));
    }
    public static void removeConnect(String connectId) {
        var connection = connections.remove(connectId);
        if (connection != null) {
            connection.getChannel().close();
        }
    }
    public static Connection getConnect(String connectId) {
        return connections.get(connectId);
    }
    public static boolean isConnectExist(String connectId) {
        return connections.containsKey(connectId);
    }
}
