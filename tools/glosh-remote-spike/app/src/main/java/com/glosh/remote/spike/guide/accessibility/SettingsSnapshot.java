package com.glosh.remote.spike.guide.accessibility;

import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;

import java.util.List;

public record SettingsSnapshot(
        int windowId,
        String packageName,
        String screenTitle,
        String fingerprint,
        List<NodeSnapshot> nodes,
        List<PairingCodeDetector.VisibleText> visibleText) {
    public SettingsSnapshot {
        packageName = safe(packageName);
        screenTitle = safe(screenTitle);
        fingerprint = safe(fingerprint);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        visibleText = visibleText == null ? List.of() : List.copyOf(visibleText);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
