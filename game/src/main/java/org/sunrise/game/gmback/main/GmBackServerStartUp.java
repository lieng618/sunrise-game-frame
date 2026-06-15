package org.sunrise.game.gmback.main;

import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * GM 后台入口。RPC 服务包默认为 {@code org.sunrise.game.gmback.service}。
 */
public class GmBackServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/gmback-config.properties");
    }
}
