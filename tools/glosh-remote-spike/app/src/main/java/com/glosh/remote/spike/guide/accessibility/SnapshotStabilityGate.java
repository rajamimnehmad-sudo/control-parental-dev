package com.glosh.remote.spike.guide.accessibility;

public final class SnapshotStabilityGate {
    private int windowId = Integer.MIN_VALUE;
    private String fingerprint = "";
    private int equivalentCount;

    public boolean observe(SettingsSnapshot snapshot) {
        if (snapshot == null) {
            reset();
            return false;
        }
        if (snapshot.windowId() == windowId && snapshot.fingerprint().equals(fingerprint)) {
            equivalentCount++;
        } else {
            windowId = snapshot.windowId();
            fingerprint = snapshot.fingerprint();
            equivalentCount = 1;
        }
        return equivalentCount >= 2;
    }

    public void reset() {
        windowId = Integer.MIN_VALUE;
        fingerprint = "";
        equivalentCount = 0;
    }
}
