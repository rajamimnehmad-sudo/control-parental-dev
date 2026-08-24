package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuideAnimationPolicyTest {
    @Test
    public void reducedMotionAndPausedHostStayStatic() {
        assertFalse(GuideAnimationPolicy.shouldAnimate(true, false));
        assertFalse(GuideAnimationPolicy.shouldAnimate(false, true));
        assertTrue(GuideAnimationPolicy.shouldAnimate(true, true));
    }
}
