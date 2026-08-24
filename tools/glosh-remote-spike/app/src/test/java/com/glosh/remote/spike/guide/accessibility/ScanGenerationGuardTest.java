package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ScanGenerationGuardTest {
    @Test
    public void staleGenerationCannotApplyHighlightOrScroll() {
        ScanGenerationGuard guard = new ScanGenerationGuard();
        SettingsSnapshot first = snapshot(7, "same");
        ScanGenerationGuard.Token token = guard.token(first);
        assertTrue(guard.isCurrent(token, first));
        guard.invalidate();
        assertFalse(guard.isCurrent(token, first));
    }

    @Test
    public void changedWindowOrFingerprintRejectsToken() {
        ScanGenerationGuard guard = new ScanGenerationGuard();
        ScanGenerationGuard.Token token = guard.token(snapshot(7, "one"));
        assertFalse(guard.isCurrent(token, snapshot(8, "one")));
        assertFalse(guard.isCurrent(token, snapshot(7, "two")));
    }

    @Test
    public void stabilityRequiresTwoEquivalentFingerprints() {
        SnapshotStabilityGate stability = new SnapshotStabilityGate();
        assertFalse(stability.observe(snapshot(7, "one")));
        assertTrue(stability.observe(snapshot(7, "one")));
        assertFalse(stability.observe(snapshot(7, "two")));
    }

    private SettingsSnapshot snapshot(int windowId, String fingerprint) {
        return new SettingsSnapshot(
                windowId, "com.android.settings", "Ajustes", fingerprint, List.of(), List.of());
    }
}
