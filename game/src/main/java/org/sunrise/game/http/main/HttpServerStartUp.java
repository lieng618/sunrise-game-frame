package org.sunrise.game.http.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collections;
import java.util.Properties;

public class HttpServerStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"./config/http-config.properties"};
        }
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        int serverId = Integer.parseInt(properties.getProperty("rpc.node.serverId"));
        String nodeType = properties.getProperty("rpc.node.type");
        System.setProperty("programName", "HttpServer-" + serverId);
        Utils.setLogLevel(properties.getProperty("log.level"));

        var rpcNode = RpcNodeManager.createRpcNode(serverId, nodeType);
        CallUtils.init(rpcNode.getNodeId(), Collections.singletonList("org.sunrise.game.http.service"), CallEnum.class);
        // 权限认证初始化
        JwtUtil.init(properties);

        rpcNode.start();

        Utils.startMemoryCheck();
    }
}
