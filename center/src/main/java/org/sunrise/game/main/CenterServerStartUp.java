package org.sunrise.game.main;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.dashboard.CenterDashboardServer;
import org.sunrise.game.rpc.center.CenterServerManager;
import org.sunrise.game.utils.Utils;

import java.util.Properties;

public class CenterServerStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] { "./config/center-config.properties" };
        }
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        System.setProperty("programName", "CenterServer-" + properties.getProperty("master.id"));
        Utils.setLogLevel(properties.getProperty("log.level"));

        int id = Integer.parseInt(properties.getProperty("master.id"));
        String ip = properties.getProperty("master.address");
        int port = Integer.parseInt(properties.getProperty("master.port"));

        // 创建中心服
        var centerServer = CenterServerManager.createCenterServer(id, ip, port);
        // 启动中心服
        centerServer.start();

        // RPC节点连接可视化服务
        int dashboardPort = Integer.parseInt(properties.getProperty("dashboard.port", "8088"));
        if (dashboardPort > 0) {
            new CenterDashboardServer(dashboardPort).start();
        }

        Utils.startMemoryCheck();
    }
}
