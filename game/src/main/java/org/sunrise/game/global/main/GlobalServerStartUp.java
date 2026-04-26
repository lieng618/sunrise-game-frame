package org.sunrise.game.global.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.db.DbService;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collections;
import java.util.Properties;

public class GlobalServerStartUp {
    public static void main(String[] args) {
        // args[0]:config path args[1]:global_id
        if (args.length == 0) {
            args = new String[] { "./config/global-config.properties", "4000" };
        }
        System.setProperty("programName", "GlobalServer-" + args[1]);
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        // 设置日志等级
        Utils.setLogLevel(properties.getProperty("log.level"));

        // 创建rpc节点
        var rpcNode = RpcNodeManager.createRpcNode(Integer.parseInt(args[1]));
        // 添加当前模块要注册的rpc
        CallUtils.init(rpcNode.getNodeId(), Collections.singletonList("org.sunrise.game.global.service"), CallEnum.class);
        // 启动
        rpcNode.start();

        // 内存检测
        Utils.startMemoryCheck();
    }
}
