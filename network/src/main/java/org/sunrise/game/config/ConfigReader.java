package org.sunrise.game.config;

import lombok.Getter;
import org.sunrise.game.log.LogCore;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    @Getter
    private static Properties prop = null;

    public static void loadConfig(String url) {
        prop = new Properties();
        try (InputStream input = new FileInputStream(url)) {
            prop.load(input);
        } catch (IOException e) {
            LogCore.ServerStartUp.error("loadConfig Failed, name = {}, reason = {}", url, e.getLocalizedMessage());
            System.exit(-1);
        }
    }

}