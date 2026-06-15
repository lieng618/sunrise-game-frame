package org.sunrise.game.game.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.init.GameServerRuntime;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * 游戏逻辑服入口。
 * <p>
 * RPC 通用启动见 {@link org.sunrise.game.rpc.node.RpcNodeManager#runMain}；
 * {@code beforeListen} 中加载 Luban 表、协议处理器及 JWT（本进程鉴权用）。
 */
public class GameServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/game-config.properties", () -> {
            GameServerRuntime.init();
            JwtUtil.init(ConfigReader.getProp());
        });
    }
}
