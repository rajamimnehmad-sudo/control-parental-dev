package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

/** Safe Settings destinations used only as accelerators for the manual Samsung guide. */
public final class SettingsNavigator {
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

    public void openGeneralSettings(Activity activity) {
        open(activity, new Intent(Settings.ACTION_SETTINGS));
    }

    public void openForStep(Activity activity, SamsungGuideStep step) {
        SamsungGuideStep.SettingsTarget target = step == null
                ? SamsungGuideStep.SettingsTarget.NONE
                : step.settingsTarget();
        switch (target) {
            case ABOUT_PHONE -> openAboutPhone(activity);
            case DEVELOPER_OPTIONS -> openDeveloperOptions(activity);
            case WIRELESS_DEBUGGING -> openWirelessDebugging(activity);
            case NONE -> openGeneralSettings(activity);
        }
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
                // Continue with the next safe Samsung Settings destination.
            }
        }
        activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }
}
