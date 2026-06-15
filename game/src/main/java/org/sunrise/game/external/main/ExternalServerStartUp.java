package org.sunrise.game.external.main;

import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * 客户端网关入口。RPC 服务包默认为 {@code org.sunrise.game.external.service}，无需额外 beforeListen。
 */
public class ExternalServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/external-config.properties");
    }
}
