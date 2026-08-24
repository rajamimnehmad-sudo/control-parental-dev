package com.glosh.remote.spike.wizard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public final class WizardPersistence {
    private static final String FILE_NAME = "glosh_remote_wizard";
    private final SharedPreferences preferences;

    public WizardPersistence(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public WizardSnapshot load(OemFamily fallbackFamily) {
        Map<String, String> values = new HashMap<>();
        for (String key : new String[] {
                WizardStateCodec.KEY_FAMILY,
                WizardStateCodec.KEY_STEP,
                WizardStateCodec.KEY_PHASE,
                WizardStateCodec.KEY_CONFIRMED,
                WizardStateCodec.KEY_WIRELESS_HELP}) {
            String value = preferences.getString(key, null);
            if (value != null) {
                values.put(key, value);
            }
        }
        return WizardStateCodec.decode(values, fallbackFamily).safeAfterProcessDeath();
    }

    public void save(WizardSnapshot snapshot) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, String> entry : WizardStateCodec.encode(snapshot).entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
