package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

import java.util.List;

public final class SettingsNavigator {
    public void openWirelessDebugging(Activity activity) {
        open(activity, SettingsRoute.wirelessDebuggingActions());
    }

    private void open(Activity activity, List<String> actions) {
        for (String action : actions) {
            Intent intent = new Intent(action);
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
