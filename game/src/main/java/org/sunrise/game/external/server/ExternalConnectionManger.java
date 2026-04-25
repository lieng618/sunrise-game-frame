package org.sunrise.game.external.server;

import io.netty.channel.Channel;
import kcp.Ukcp;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalConnectionManger {
    private static final ConcurrentHashMap<Long, ClientConnection> clients = new ConcurrentHashMap<>();
    // 连接id计数器
    private static final AtomicInteger connectionIdAuto = new AtomicInteger();
    // 当前连接数计数器
    private static final AtomicInteger onlineCount = new AtomicInteger(0);

    public static ClientConnection createClientConnect(Channel channel) {
        var connection = new ClientConnection(channel);
        connection.setId((long) RpcNodeManager.getRpcServerId() * Utils.ID_BASE_NUM + connectionIdAuto.incrementAndGet());
        clients.put(connection.getId(), connection);
        onlineCount.incrementAndGet();
        return connection;
    }

    public static ClientConnection createClientConnect(Ukcp ukcp) {
        var connection = new ClientConnection(ukcp);
        connection.setId((long) RpcNodeManager.getRpcServerId() * Utils.ID_BASE_NUM + connectionIdAuto.incrementAndGet());
        clients.put(connection.getId(), connection);
        onlineCount.incrementAndGet();
        return connection;
    }

    public static void removeClientConnect(long id) {
        ClientConnection remove = clients.remove(id);
        if (remove != null) {
            onlineCount.decrementAndGet();
        }
    }

    public static ClientConnection getClientConnect(long id) {
        return clients.get(id);
    }

    public static Collection<ClientConnection> getClientConnections() {
        return clients.values();
    }

    public static int getOnlineCount() {
        return onlineCount.get();
    }
}