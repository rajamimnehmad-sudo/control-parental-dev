package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ScreenAwakeLeaseTest {
    @Test
    public void capturesOnceAndRestoresExactValues() throws Exception {
        MemoryStore store = new MemoryStore();
        FakeSettings settings = new FakeSettings("3", "30000");
        ScreenAwakeLease lease = new ScreenAwakeLease(store);

        lease.acquire(settings);
        assertEquals("7", settings.plugged);
        assertEquals("2147483647", settings.timeout);

        lease.ensureApplied(settings);
        lease.release(settings);
        assertEquals("3", settings.plugged);
        assertEquals("30000", settings.timeout);
        assertNull(store.snapshot);
    }

    @Test
    public void processRestartUsesOriginalPersistedSnapshot() throws Exception {
        MemoryStore store = new MemoryStore();
        FakeSettings settings = new FakeSettings("1", "60000");
        new ScreenAwakeLease(store).acquire(settings);

        settings.plugged = "0";
        settings.timeout = "15000";
        ScreenAwakeLease restarted = new ScreenAwakeLease(store);
        restarted.ensureApplied(settings);
        restarted.release(settings);

        assertEquals("1", settings.plugged);
        assertEquals("60000", settings.timeout);
    }

    private static final class MemoryStore implements ScreenAwakeLease.StateStore {
        private ScreenAwakeLease.Snapshot snapshot;

        @Override
        public ScreenAwakeLease.Snapshot load() {
            return snapshot;
        }

        @Override
        public void save(ScreenAwakeLease.Snapshot value) {
            snapshot = value;
        }

        @Override
        public void clear() {
            snapshot = null;
        }
    }

    private static final class FakeSettings implements ScreenAwakeLease.SettingsAccess {
        private String plugged;
        private String timeout;

        private FakeSettings(String plugged, String timeout) {
            this.plugged = plugged;
            this.timeout = timeout;
        }

        @Override
        public String readStayAwakeWhilePluggedIn() {
            return plugged;
        }

        @Override
        public void writeStayAwakeWhilePluggedIn(String value) {
            plugged = value;
        }

        @Override
        public String readScreenOffTimeout() {
            return timeout;
        }

        @Override
        public void writeScreenOffTimeout(String value) {
            timeout = value;
        }
    }
}
