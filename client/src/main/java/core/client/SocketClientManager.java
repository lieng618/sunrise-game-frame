package core.client;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class SocketClientManager {
    @Getter
    private static ConcurrentHashMap<String, SocketClient> clients = new ConcurrentHashMap<>();
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
