package com.glosh.remote.spike.wizard;

import android.provider.Settings;

import java.util.List;
import java.util.function.Predicate;

public final class SettingsRoute {
    public static final String WIRELESS_DEBUGGING = "android.settings.WIRELESS_DEBUGGING_SETTINGS";

    private SettingsRoute() {
    }

    public static List<String> aboutPhoneActions() {
        return List.of(Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS);
    }

    public static List<String> wirelessDebuggingActions() {
        return List.of(
                WIRELESS_DEBUGGING,
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                Settings.ACTION_SETTINGS);
    }

    public static String firstResolvable(List<String> actions, Predicate<String> resolver) {
        for (String action : actions) {
            if (resolver.test(action)) {
                return action;
            }
        }
        return Settings.ACTION_SETTINGS;
    }
}
