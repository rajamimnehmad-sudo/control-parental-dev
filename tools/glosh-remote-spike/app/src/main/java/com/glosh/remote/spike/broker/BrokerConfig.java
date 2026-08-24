package com.glosh.remote.spike.broker;

import com.glosh.remote.spike.BuildConfig;

public final class BrokerConfig {
    private BrokerConfig() {
    }

    public static String baseUrl() {
        String value = BuildConfig.BROKER_BASE_URL == null ? "" : BuildConfig.BROKER_BASE_URL.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.startsWith("https://") ? value : "";
    }
}
