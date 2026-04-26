package org.sunrise.game.game.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.db.DbService;
import org.sunrise.game.game.modules.ModuleUtils;
import org.sunrise.game.game.logic.ConfigUtils;
import org.sunrise.game.game.logic.LogicUtils;
import org.sunrise.game.game.logic.ProtoParserUtils;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.Collections;
import java.util.Properties;

public class GameServerStartUp {
    public static void main(String[] args) {
        // args[0]:config path args[1]:gameId
        if (args.length == 0) {
            args = new String[]{ "./config/game-config.properties", "200"};
        }
        System.setProperty("programName", "GameServer-" + args[1]);
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        // 设置日志等级
        Utils.setLogLevel(properties.getProperty("log.level"));

        // 创建rpc节点
        var rpcNode = RpcNodeManager.createRpcNode(Integer.parseInt(args[1]));
        // rpc初始化
        CallUtils.init(rpcNode.getNodeId(), Collections.singletonList("org.sunrise.game.game.service"), CallEnum.class);

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

        rpcNode.start();

        // 内存检测
        Utils.startMemoryCheck();
    }
}
