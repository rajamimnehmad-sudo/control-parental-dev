package com.glosh.remote.spike.adb;

import android.content.Context;
import android.content.SharedPreferences;

/** Captures Android screen timeout settings once, applies an awake lease, then restores exactly. */
public final class ScreenAwakeLease {
    static final String AWAKE_PLUGGED_VALUE = "7";
    static final String AWAKE_TIMEOUT_VALUE = "2147483647";

    public interface SettingsAccess {
        String readStayAwakeWhilePluggedIn() throws Exception;
        void writeStayAwakeWhilePluggedIn(String value) throws Exception;
        String readScreenOffTimeout() throws Exception;
        void writeScreenOffTimeout(String value) throws Exception;
    }

    interface StateStore {
        Snapshot load();
        void save(Snapshot snapshot);
        void clear();
    }

    static final class Snapshot {
        final String plugged;
        final String timeout;

        Snapshot(String plugged, String timeout) {
            this.plugged = normalize(plugged);
            this.timeout = normalize(timeout);
        }

        private static String normalize(String value) {
            String normalized = value == null ? "null" : value.trim();
            return normalized.isEmpty() ? "null" : normalized;
        }
    }

    private final StateStore store;

    public ScreenAwakeLease(Context context) {
        this(new PreferencesStore(context.getApplicationContext()));
    }

    ScreenAwakeLease(StateStore store) {
        this.store = store;
    }

    public synchronized void acquire(SettingsAccess settings) throws Exception {
        Snapshot snapshot = store.load();
        if (snapshot == null) {
            snapshot = new Snapshot(
                    settings.readStayAwakeWhilePluggedIn(),
                    settings.readScreenOffTimeout());
            // Persist before mutating Android so a process death never loses the values to restore.
            store.save(snapshot);
        }
        settings.writeStayAwakeWhilePluggedIn(AWAKE_PLUGGED_VALUE);
        settings.writeScreenOffTimeout(AWAKE_TIMEOUT_VALUE);
    }

    public synchronized void ensureApplied(SettingsAccess settings) throws Exception {
        if (store.load() == null) {
            acquire(settings);
            return;
        }
        settings.writeStayAwakeWhilePluggedIn(AWAKE_PLUGGED_VALUE);
        settings.writeScreenOffTimeout(AWAKE_TIMEOUT_VALUE);
    }

    public synchronized void release(SettingsAccess settings) throws Exception {
        Snapshot snapshot = store.load();
        if (snapshot == null) {
            return;
        }
        settings.writeStayAwakeWhilePluggedIn(snapshot.plugged);
        settings.writeScreenOffTimeout(snapshot.timeout);
        store.clear();
    }

    private static final class PreferencesStore implements StateStore {
        private static final String PREFS = "glosh_remote_screen_awake_lease_v1";
        private static final String ACTIVE = "active";
        private static final String PLUGGED = "plugged";
        private static final String TIMEOUT = "timeout";
        private final SharedPreferences prefs;

        private PreferencesStore(Context context) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @Override
        public Snapshot load() {
            if (!prefs.getBoolean(ACTIVE, false)) {
                return null;
            }
            return new Snapshot(
                    prefs.getString(PLUGGED, "null"),
                    prefs.getString(TIMEOUT, "null"));
        }

        @Override
        public void save(Snapshot snapshot) {
            boolean committed = prefs.edit()
                    .putString(PLUGGED, snapshot.plugged)
                    .putString(TIMEOUT, snapshot.timeout)
                    .putBoolean(ACTIVE, true)
                    .commit();
            if (!committed) {
                throw new IllegalStateException("Could not persist screen-awake lease.");
            }
        }

        @Override
        public void clear() {
            prefs.edit().clear().commit();
        }
    }
}
