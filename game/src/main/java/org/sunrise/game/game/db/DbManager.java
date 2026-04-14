package org.sunrise.game.game.db;

import org.sunrise.game.db.DbService;
import org.sunrise.game.rpc.node.RpcNodeManager;

public class DbManager {
    public static DbService getDbService() {
        return RpcNodeManager.getRpcNode().getDbService();
    }

}
