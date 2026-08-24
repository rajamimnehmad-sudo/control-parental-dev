package com.glosh.remote.spike.wizard;

public final class GuideAnimationPolicy {
    private GuideAnimationPolicy() {
    }

    public static boolean shouldAnimate(boolean hostActive, boolean animatorsEnabled) {
        return hostActive && animatorsEnabled;
    }
}
