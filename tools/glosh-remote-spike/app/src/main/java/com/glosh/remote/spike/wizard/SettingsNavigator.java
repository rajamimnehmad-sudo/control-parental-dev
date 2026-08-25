package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;

import com.glosh.remote.spike.guide.accessibility.LiveGuideAccessibilityService;

public final class SettingsNavigator {
    private static final String ACCESSIBILITY_DETAILS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

    public void openAboutPhone(Activity activity) {
        open(activity,
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    public void openWirelessDebugging(Activity activity) {
        open(activity,
                new Intent(SettingsRoute.WIRELESS_DEBUGGING),
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    public void openDeveloperOptions(Activity activity) {
        open(activity,
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    public void openAccessibility(Activity activity) {
        ComponentName service = new ComponentName(activity, LiveGuideAccessibilityService.class);
        Intent details = new Intent(ACCESSIBILITY_DETAILS)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, service);
        open(activity,
                details,
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    private void open(Activity activity, Intent... intents) {
        for (Intent intent : intents) {
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                continue;
            }
            try {
                activity.startActivity(intent);
                return;
            } catch (Throwable ignored) {
                // Continue with the next safe Settings destination.
            }
        }
        activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }
}
