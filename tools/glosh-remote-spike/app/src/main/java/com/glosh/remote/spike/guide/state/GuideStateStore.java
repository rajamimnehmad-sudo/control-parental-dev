package com.glosh.remote.spike.guide.state;

import android.content.Context;
import android.content.SharedPreferences;

import com.glosh.remote.spike.wizard.OemFamily;

import java.util.Set;

public final class GuideStateStore {
    private static final String FILE = "glosh_remote_live_guide";
    private static final String KEY_STAGE = "guide_stage";
    private static final String KEY_FAMILY = "oem_family";
    private static final String KEY_ACTIVE = "onboarding_active";
    private static final Set<String> SAFE_KEYS = Set.of(KEY_STAGE, KEY_FAMILY, KEY_ACTIVE);

    private final SharedPreferences preferences;

    public GuideStateStore(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public Snapshot load(OemFamily fallback) {
        GuideStage stage = enumValue(
                GuideStage.class,
                preferences.getString(KEY_STAGE, null),
                GuideStage.OFF);
        OemFamily family = enumValue(
                OemFamily.class,
                preferences.getString(KEY_FAMILY, null),
                fallback);
        boolean active = preferences.getBoolean(KEY_ACTIVE, false);
        if (stage == GuideStage.PAIRING || stage == GuideStage.CONNECTED) {
            stage = GuideStage.SUPPORT_PREPARING;
        }
        return new Snapshot(stage, family, active);
    }

    public void save(Snapshot snapshot) {
        preferences.edit()
                .clear()
                .putString(KEY_STAGE, snapshot.stage().name())
                .putString(KEY_FAMILY, snapshot.family().name())
                .putBoolean(KEY_ACTIVE, snapshot.onboardingActive())
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public static Set<String> persistedKeys() {
        return SAFE_KEYS;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw == null ? "" : raw);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    public record Snapshot(GuideStage stage, OemFamily family, boolean onboardingActive) {
    }
}
