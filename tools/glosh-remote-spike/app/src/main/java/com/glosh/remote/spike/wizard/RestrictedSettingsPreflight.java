package com.glosh.remote.spike.wizard;

import android.content.Context;
import android.os.Build;

/**
 * One-time UX preflight for Android's restricted-settings protection on sideloaded builds.
 *
 * <p>Android does not expose a public API that tells a normal app whether the user has already
 * chosen "Allow restricted settings". Glosh therefore treats Android 13+ sideloaded support builds
 * conservatively: show the instruction once, let the user confirm it, then proceed to Accessibility.
 * The confirmation is local to this app install and can always be reset by clearing app data.</p>
 */
public final class RestrictedSettingsPreflight {
    private static final String PREFS = "glosh_remote_restricted_settings";
    private static final String KEY_CONFIRMED = "confirmed";

    private RestrictedSettingsPreflight() {
    }

    public static boolean required(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONFIRMED, false);
    }

    public static void confirm(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONFIRMED, true)
                .apply();
    }
}
