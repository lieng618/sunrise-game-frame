package org.sunrise.game.runone.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.init.GameServerRuntime;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * 单进程合一模式入口（External + Game + Http + GmBack 同 JVM）。
 * <p>
 * 须在配置中设置更宽的 {@code rpc.scan.packages=org.sunrise.game}，并在 {@code beforeListen} 中
 * 执行与 GameServer 相同的逻辑层初始化及 JWT 配置。
 */
public class RunAllOneServerStartUp {
    public static void main(String[] args) {
        RpcNodeManager.runMain(args, "./config/runallone-config.properties", () -> {
            GameServerRuntime.init();
            JwtUtil.init(ConfigReader.getProp());
        });
    }
}
