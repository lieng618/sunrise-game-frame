package org.sunrise.game.config;

import lombok.Getter;
import org.sunrise.game.log.LogCore;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigReader {

    @Getter
    private static Properties prop = null;

    /** Directory containing the loaded *.properties file; used to resolve relative data paths. */
    @Getter
    private static Path configFileDirectory = null;

    public static void loadConfig(String url) {
        Path configFile = Paths.get(url).toAbsolutePath().normalize();
        configFileDirectory = configFile.getParent();

        Properties raw = new Properties();
        try (InputStream input = new FileInputStream(configFile.toFile())) {
            raw.load(input);
        } catch (IOException e) {
            LogCore.ServerStartUp.error("loadConfig Failed, name = {}, reason = {}", url, e.getLocalizedMessage());
            System.exit(-1);
        }
        try {
            prop = ConfigPlaceholderResolver.resolveAll(raw);
        } catch (IllegalStateException e) {
            LogCore.ServerStartUp.error("loadConfig Failed, name = {}, reason = {}", url, e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Resolve a configured path to an absolute normalized path.
     * Absolute paths are returned as-is; relative paths are resolved against the
     * directory of the loaded properties file (not the process working directory).
     */
    public static Path resolvePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isEmpty()) {
            throw new IllegalArgumentException("configured path is empty");
        }
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (configFileDirectory != null) {
            return configFileDirectory.resolve(path).normalize();
        }
        return path.normalize().toAbsolutePath();
    }

}
