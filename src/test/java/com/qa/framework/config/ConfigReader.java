package com.qa.framework.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties once (thread-safe, lazy holder idiom).
 * Lookup order: JVM system property (-Dkey=value) -> environment variable (KEY_NAME) -> config file.
 */
public final class ConfigReader {

    private static final String CONFIG_PATH = "src/test/resources/config/config.properties";

    private ConfigReader() {
    }

    private static final class Holder {
        private static final Properties PROPS = load();

        private static Properties load() {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load config file: " + CONFIG_PATH, e);
            }
            return props;
        }
    }

    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String value = Holder.PROPS.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return value.trim();
    }

    public static String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
