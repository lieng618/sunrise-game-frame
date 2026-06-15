package org.sunrise.game.global.main;

import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * 跨服全局服入口（聊天、好友、邮件等）。RPC 服务包默认为 {@code org.sunrise.game.global.service}。
 */
public class GlobalServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/global-config.properties");
    }
}
