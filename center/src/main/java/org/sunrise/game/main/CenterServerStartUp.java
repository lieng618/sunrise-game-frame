package org.sunrise.game.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.rpc.center.CenterServerManager;
import org.sunrise.game.utils.Utils;

import java.util.Properties;

public class CenterServerStartUp {
    public static void main(String[] args) {
        // args[0]:config path args[1]:center_id
        if (args.length == 0) {
            args = new String[] { "./config/center-config.properties", "1" };
        }
        System.setProperty("programName", "CenterServer-" + args[1]);
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        // 设置日志等级
        Utils.setLogLevel(properties.getProperty("log.level"));

        int id = Integer.parseInt(properties.getProperty("master.id"));
        String ip = properties.getProperty("master.address");
        int port = Integer.parseInt(properties.getProperty("master.port"));

        // 创建中心服
        var centerServer = CenterServerManager.createCenterServer(id, ip, port);
        // 启动中心服
        centerServer.start();

        // 内存检测
        Utils.startMemoryCheck();
    }
}
