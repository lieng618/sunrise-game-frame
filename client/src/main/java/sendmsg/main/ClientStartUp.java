package sendmsg.main;

import core.client.LoginManager;
import sendmsg.frame.MainFrame;
import core.message.MessageUtil;
import org.sunrise.game.config.ConfigReader;

import java.util.Properties;

public class ClientStartUp {
    public static void main(String[] args) {
        // args[0]:config path
        if (args.length == 0) {
            args = new String[]{"./config/client-config.properties"};
        }
        System.setProperty("programName", "Client");
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        String httpUrl = properties.getProperty("http.address") + ":" + properties.getProperty("http.port");

        LoginManager.initialize(httpUrl);
        MessageUtil.init();
        MainFrame.start();
    }
}
