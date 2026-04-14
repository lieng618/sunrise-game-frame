package org.sunrise.game.gmback.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.db.DbService;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collections;
import java.util.Properties;

public class GmBackServerStartUp {
    public static void main(String[] args) {
        // args[0]:config path args[1]:global_id
        if (args.length == 0) {
            args = new String[] { "./config/gmback-config.properties", "2" };
        }
        System.setProperty("programName", "GmBackServer-" + args[1]);
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
        CallUtils.init(rpcNode.getNodeId(), Collections.singletonList("org.sunrise.game.gmback.service"), CallEnum.class);
        // 启动
        rpcNode.start(new DbService());
        // 内存检测
        Utils.startMemoryCheck();
    }
}
