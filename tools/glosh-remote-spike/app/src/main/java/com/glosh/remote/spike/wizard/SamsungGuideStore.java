package com.glosh.remote.spike.wizard;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny durable store so returning from Settings resumes the same Samsung instruction. */
public final class SamsungGuideStore {
    private static final String PREFS = "glosh_remote_samsung_guide";
    private static final String KEY_STEP = "step";
    private static final String KEY_ACTIVE = "active";

    private final SharedPreferences preferences;

    public SamsungGuideStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public SamsungGuideStep step() {
        String raw = preferences.getString(KEY_STEP, SamsungGuideStep.ABOUT_PHONE.name());
        try {
            return SamsungGuideStep.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return SamsungGuideStep.ABOUT_PHONE;
        }
    }

    public boolean active() {
        return preferences.getBoolean(KEY_ACTIVE, false);
    }

    public void begin() {
        save(SamsungGuideStep.ABOUT_PHONE, true);
    }

    public void setStep(SamsungGuideStep step) {
        save(step, true);
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private void save(SamsungGuideStep step, boolean active) {
        preferences.edit()
                .putString(KEY_STEP, step.name())
                .putBoolean(KEY_ACTIVE, active)
                .apply();
    }
}
