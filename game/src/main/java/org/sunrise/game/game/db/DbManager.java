package org.sunrise.game.game.db;

import org.sunrise.game.db.DbService;
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

}
