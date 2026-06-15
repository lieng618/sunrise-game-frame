package org.sunrise.game.http.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * HTTP 服入口。{@code beforeListen} 中初始化 JWT（注册/登录签发 token）。
 */
public class HttpServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/http-config.properties", () ->
                JwtUtil.init(ConfigReader.getProp()));
    }
}
