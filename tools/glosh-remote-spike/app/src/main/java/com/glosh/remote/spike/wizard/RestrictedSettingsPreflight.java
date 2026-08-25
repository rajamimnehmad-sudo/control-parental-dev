package com.glosh.remote.spike.wizard;

import android.content.Context;
import android.os.Build;

/**
 * Tracks whether the customer has already attempted to enable Accessibility in this guided run.
 *
 * <p>Android does not expose a public API that tells a normal app whether "Allow restricted
 * settings" is currently granted, and Samsung may not expose the App Info overflow menu until
 * Android has actually rejected the Accessibility activation attempt. Therefore Glosh never uses a
 * local confirmation bit as permission authority. Accessibility enabled state remains the only
 * success signal.</p>
 */
public final class RestrictedSettingsPreflight {
    private static final String PREFS = "glosh_remote_restricted_settings";
    private static final String KEY_ACCESSIBILITY_ATTEMPTED = "accessibility_attempted";

    private RestrictedSettingsPreflight() {
    }

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static void markAccessibilityAttempt(Context context) {
        if (!supported()) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACCESSIBILITY_ATTEMPTED, true)
                .apply();
    }

    public static boolean shouldOfferRecovery(Context context, boolean accessibilityEnabled) {
        boolean attempted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCESSIBILITY_ATTEMPTED, false);
        return shouldOfferRecovery(supported(), attempted, accessibilityEnabled);
    }

    static boolean shouldOfferRecovery(
            boolean android13OrNewer,
            boolean accessibilityAttempted,
            boolean accessibilityEnabled) {
        return android13OrNewer && accessibilityAttempted && !accessibilityEnabled;
    }

    public static void clearAttempt(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ACCESSIBILITY_ATTEMPTED)
                .apply();
    }
}
