package org.sunrise.game.runone.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.logic.ConfigUtils;
import org.sunrise.game.game.logic.LogicUtils;
import org.sunrise.game.game.logic.ProtoParserUtils;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.modules.ModuleUtils;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collections;
import java.util.Properties;

public class RunAllOneServerStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"./config/runallone-config.properties"};
        }
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        int serverId = Integer.parseInt(properties.getProperty("rpc.node.serverId"));
        String nodeType = properties.getProperty("rpc.node.type");
        System.setProperty("programName", "RunAllOneServer-" + serverId);
        // 设置日志等级
        Utils.setLogLevel(properties.getProperty("log.level"));

        // 创建rpc节点
        var rpcNode = RpcNodeManager.createRpcNode(serverId, nodeType);
        // 添加当前模块要注册的rpc
        CallUtils.init(rpcNode.getNodeId(), Collections.singletonList("org.sunrise.game"), CallEnum.class);

        // 加载配置文件
        ConfigUtils.load();
        // 协议解析初始化
        ProtoParserUtils.init();
        // 协议处理函数初始化
        LogicUtils.init(Collections.singletonList("org.sunrise.game.game.logic"));
        // 玩家模块工厂初始化
        ModuleUtils.init(Collections.singletonList("org.sunrise.game.game.modules"));
        // 系统模块初始化
        GameSystemUtils.init(Collections.singletonList("org.sunrise.game.game.logic.system"));

        // 启动
        rpcNode.start();

        // 内存检测
        Utils.startMemoryCheck();
    }
}
