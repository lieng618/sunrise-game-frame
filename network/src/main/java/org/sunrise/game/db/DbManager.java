package org.sunrise.game.db;

import org.sunrise.game.graceful.OnShutdown;
import org.sunrise.game.rpc.node.RpcNodeManager;

public class DbManager {

    private static DbService dbService;

    public static DbService getDbService() {
        if (dbService == null) {
            if (RpcNodeManager.getRpcNode() != null) {
                return RpcNodeManager.getRpcNode().getDbService();
            }
            dbService = new DbService();
        }
        return dbService;
    }

    @OnShutdown(order = 100, timeoutMs = 5_000)
    public static void closeDb() {
        if (dbService != null) {
            dbService.close();
        }
        if (RpcNodeManager.getRpcNode() != null) {
            RpcNodeManager.getRpcNode().getDbService().close();
        }
    }

}
