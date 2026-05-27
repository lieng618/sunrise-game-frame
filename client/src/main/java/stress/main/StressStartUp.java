package stress.main;

import ch.qos.logback.classic.Level;
import core.client.LoginManager;
import core.client.StressManager;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;
import stress.frame.StressFrame;

import java.util.Properties;

public class StressStartUp {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"./config/client-config.properties"};
        }
        System.setProperty("programName", "Stress");
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        String httpUrl = properties.getProperty("http.address") + ":" + properties.getProperty("http.port");
        Utils.setLogLevel(properties.getProperty("log.level"));
        LogCore.setLogLevel("kcp", Level.WARN);
        LoginManager.initialize(httpUrl);
        StressManager.initialize();
        StressFrame.start();
    }
}
