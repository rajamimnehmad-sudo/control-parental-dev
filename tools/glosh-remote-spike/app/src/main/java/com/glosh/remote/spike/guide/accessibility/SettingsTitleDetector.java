package com.glosh.remote.spike.guide.accessibility;

public final class SettingsTitleDetector {
    private SettingsTitleDetector() {
    }

    public static boolean isTitleContainer(String viewId) {
        if (viewId == null) {
            return false;
        }
        String normalized = TargetMatcher.normalize(viewId);
        return normalized.contains("collapsing_toolbar")
                || normalized.contains("toolbar_title")
                || normalized.contains("action_bar");
    }
}
