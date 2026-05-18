package org.sunrise.game.rpc.center;

import lombok.Getter;

public class CenterServerManager {
    @Getter
    private static CenterServer centerServer;

    public static CenterServer createCenterServer(int id, String ip, int port) {
        centerServer = new CenterServer(id, ip, port);
        return centerServer;
    }

    public static String getCenterServerNodeId() {
        if (centerServer != null) {
            return centerServer.getNodeId();
        }
        return "";
    }
}
