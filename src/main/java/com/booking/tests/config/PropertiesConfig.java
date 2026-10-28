package com.booking.tests.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Resolves each key once, first hit wins:
 * {@code -Dkey} > env var {@code KEY_NAME} > {@code config/<env>.properties} (when {@code -Denv} is set)
 * > {@code config/defaults.properties}. Fails fast on missing keys, bad values or an unknown env file.
 */
final class PropertiesConfig implements Config {

    private static final Logger log = LoggerFactory.getLogger(PropertiesConfig.class);

    private final Properties fileProps = new Properties();
    private final String uiBaseUrl;
    private final String apiBaseUrl;
    private final String browser;
    private final boolean headless;
    private final long maxResponseMs;

    private PropertiesConfig() {
        fileProps.putAll(load("config/defaults.properties"));
        String env = System.getProperty("env");
        if (!isBlank(env)) {
            fileProps.putAll(load("config/" + env.trim() + ".properties"));
        }

        uiBaseUrl = value("ui.base.url");
        apiBaseUrl = value("api.base.url");
        browser = value("ui.browser");
        headless = booleanValue("ui.headless");
        maxResponseMs = longValue("api.max.response.ms");

        log.info("Config: env={}, ui.base.url={}, api.base.url={}, ui.browser={}, ui.headless={}, api.max.response.ms={}",
                isBlank(env) ? "defaults" : env, uiBaseUrl, apiBaseUrl, browser, headless, maxResponseMs);
    }

    static PropertiesConfig instance() {
        return Holder.INSTANCE;
    }

    @Override
    public String uiBaseUrl() {
        return uiBaseUrl;
    }

    @Override
    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    @Override
    public String browser() {
        return browser;
    }

    @Override
    public boolean headless() {
        return headless;
    }

    @Override
    public long maxResponseMs() {
        return maxResponseMs;
    }

    private String value(String key) {
        String value = System.getProperty(key);
        if (isBlank(value)) {
            value = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        }
        if (isBlank(value)) {
            value = fileProps.getProperty(key);
        }
        if (isBlank(value)) {
            throw new IllegalStateException("Missing config key '" + key + "'");
        }
        return value.trim();
    }

    private boolean booleanValue(String key) {
        String value = value(key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException("Config key '" + key + "' must be true or false, was '" + value + "'");
        }
        return Boolean.parseBoolean(value);
    }

    private long longValue(String key) {
        String value = value(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Config key '" + key + "' must be a number, was '" + value + "'", e);
        }
    }

    private static Properties load(String resource) {
        InputStream in = PropertiesConfig.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Config file not found on classpath: " + resource);
        }
        Properties props = new Properties();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config file " + resource, e);
        }
        return props;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Holder {
        private static final PropertiesConfig INSTANCE = new PropertiesConfig();
    }
}
