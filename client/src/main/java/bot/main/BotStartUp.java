package bot.main;

import bot.frame.BotFrame;
import ch.qos.logback.classic.Level;
import core.client.BotManager;
import core.client.LoginManager;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;

import java.util.Properties;

public class BotStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"./config/client-config.properties"};
        }
        System.setProperty("programName", "Bot");
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        String httpUrl = properties.getProperty("http.address") + ":" + properties.getProperty("http.port");
        LogCore.setLogLevel("kcp", Level.WARN);
        LoginManager.initialize(httpUrl);
        BotManager.initialize();
        BotFrame.start();
    }
}
