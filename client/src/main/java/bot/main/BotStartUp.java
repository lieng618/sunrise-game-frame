package bot.main;

import bot.frame.BotFrame;
import core.client.BotManager;
import core.client.LoginManager;
import org.sunrise.game.config.ConfigReader;

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

        LoginManager.initialize(httpUrl);
        BotManager.initialize();
        BotFrame.start();
    }
}
