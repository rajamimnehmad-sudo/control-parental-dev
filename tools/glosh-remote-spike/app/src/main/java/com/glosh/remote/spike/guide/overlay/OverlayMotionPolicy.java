package com.glosh.remote.spike.guide.overlay;

public final class OverlayMotionPolicy {
    private OverlayMotionPolicy() {
    }

    public static boolean shouldPulse(boolean animatorsEnabled) {
        return animatorsEnabled;
    }
}
