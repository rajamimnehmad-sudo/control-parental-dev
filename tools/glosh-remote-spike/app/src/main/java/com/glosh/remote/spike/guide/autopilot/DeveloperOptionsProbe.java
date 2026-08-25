package com.glosh.remote.spike.guide.autopilot;

import android.content.Context;
import android.provider.Settings;

/** Reads Android's authoritative developer-options enabled state. */
public final class DeveloperOptionsProbe {
    private DeveloperOptionsProbe() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
