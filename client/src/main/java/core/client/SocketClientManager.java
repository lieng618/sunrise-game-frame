package core.client;

import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;

public class SocketClientManager {
    @Getter
    private static final ConcurrentHashMap<String, SocketClient> clients = new ConcurrentHashMap<>();
    public static void addClient(SocketClient client) {
        clients.put(client.uid, client);
    }

    public static void removeClient(String uid) {
        clients.remove(uid);
    }

    public static SocketClient getClient(String uid) {
        return clients.get(uid);
    }

}
