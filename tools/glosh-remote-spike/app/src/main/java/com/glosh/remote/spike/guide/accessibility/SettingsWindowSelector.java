package com.glosh.remote.spike.guide.accessibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SettingsWindowSelector {
    public enum WindowType {
        APPLICATION,
        ACCESSIBILITY_OVERLAY,
        INPUT_METHOD,
        OTHER
    }

    public record WindowCandidate(
            int id,
            WindowType type,
            String packageName,
            boolean active,
            boolean focused) {
    }

    public record Selection(WindowCandidate window, boolean ambiguous) {
        public boolean selected() {
            return window != null && !ambiguous;
        }
    }

    public Selection select(List<WindowCandidate> windows, Set<String> trustedSettingsPackages) {
        if (windows == null || trustedSettingsPackages == null || trustedSettingsPackages.isEmpty()) {
            return new Selection(null, false);
        }
        List<WindowCandidate> settings = new ArrayList<>();
        for (WindowCandidate candidate : windows) {
            if (candidate != null
                    && candidate.type() == WindowType.APPLICATION
                    && trustedSettingsPackages.contains(candidate.packageName())) {
                settings.add(candidate);
            }
        }
        return settings.size() == 1
                ? new Selection(settings.get(0), false)
                : new Selection(null, settings.size() > 1);
    }
}
