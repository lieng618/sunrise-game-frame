package org.sunrise.game.config;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves ${ENV} and ${ENV:default} placeholders in config values.
 * Lookup order: environment variable → system property → default (if present).
 */
public final class ConfigPlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?\\}");

    private ConfigPlaceholderResolver() {
    }

    public static String resolve(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1).trim();
            String defaultValue = matcher.group(2);
            String resolved = lookup(envName);
            if (resolved == null) {
                resolved = defaultValue;
            }
            if (resolved == null) {
                throw new IllegalStateException(
                        "Missing required config placeholder: ${" + envName + "} (no default provided)");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static Properties resolveAll(Properties source) {
        Properties resolved = new Properties();
        for (String key : source.stringPropertyNames()) {
            resolved.setProperty(key, resolve(source.getProperty(key)));
        }
        return resolved;
    }

    private static String lookup(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return null;
    }
}
