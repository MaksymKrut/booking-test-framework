package com.booking.tests.config;

/**
 * Run configuration. Every key can be overridden with {@code -Dkey=value} or an env var
 * ({@code ui.base.url} → {@code UI_BASE_URL}); see {@link PropertiesConfig} for precedence.
 */
public interface Config {

    String uiBaseUrl();

    String apiBaseUrl();

    String browser();

    boolean headless();

    long maxResponseMs();

    static Config get() {
        return PropertiesConfig.instance();
    }
}
