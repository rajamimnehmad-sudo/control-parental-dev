package com.glosh.remote.spike.wizard;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WizardStateCodec {
    public static final String KEY_FAMILY = "oem_family";
    public static final String KEY_STEP = "wizard_step";
    public static final String KEY_PHASE = "developer_phase";
    public static final String KEY_CONFIRMED = "developer_confirmed";
    public static final String KEY_WIRELESS_HELP = "wireless_help";

    private WizardStateCodec() {
    }

    public static Map<String, String> encode(WizardSnapshot snapshot) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_FAMILY, snapshot.family().name());
        values.put(KEY_STEP, snapshot.step().name());
        values.put(KEY_PHASE, snapshot.developerPhase().name());
        values.put(KEY_CONFIRMED, Boolean.toString(snapshot.developerConfirmed()));
        values.put(KEY_WIRELESS_HELP, Boolean.toString(snapshot.wirelessHelp()));
        return values;
    }

    public static WizardSnapshot decode(Map<String, String> values, OemFamily fallbackFamily) {
        return new WizardSnapshot(
                enumValue(OemFamily.class, values.get(KEY_FAMILY), fallbackFamily),
                enumValue(OnboardingState.Step.class, values.get(KEY_STEP), OnboardingState.Step.HOME),
                enumValue(DeveloperGuidePhase.class, values.get(KEY_PHASE), DeveloperGuidePhase.GUIDE),
                Boolean.parseBoolean(values.getOrDefault(KEY_CONFIRMED, "false")),
                Boolean.parseBoolean(values.getOrDefault(KEY_WIRELESS_HELP, "false")));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw == null ? "" : raw);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
