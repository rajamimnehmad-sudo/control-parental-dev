package com.glosh.remote.spike.guide.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayGeometryTest {
    @Test
    public void highlightRespectsInsetsAndRotationSizedDisplay() {
        OverlayGeometry.Box result = OverlayGeometry.clamp(
                new OverlayGeometry.Box(0, 0, 1200, 900),
                new OverlayGeometry.Box(0, 0, 1000, 600),
                new OverlayGeometry.EdgeInsets(20, 30, 40, 50));
        assertTrue(result.left() >= 20);
        assertTrue(result.top() >= 30);
        assertTrue(result.right() <= 960);
        assertTrue(result.bottom() <= 550);
    }

    @Test
    public void bubbleAvoidsTargetAndReducedMotionIsStatic() {
        OverlayGeometry.Box target = new OverlayGeometry.Box(100, 300, 900, 400);
        OverlayGeometry.Position point = OverlayGeometry.placeBubble(
                new OverlayGeometry.Box(0, 0, 1000, 1000), target, 300, 160,
                new OverlayGeometry.EdgeInsets(0, 0, 0, 0));
        OverlayGeometry.Box bubble = new OverlayGeometry.Box(
                point.x(), point.y(), point.x() + 300, point.y() + 160);
        assertFalse(intersects(target, bubble));
        assertFalse(OverlayMotionPolicy.shouldPulse(false));
        assertTrue(OverlayMotionPolicy.shouldPulse(true));
    }

    private boolean intersects(OverlayGeometry.Box first, OverlayGeometry.Box second) {
        return first.left() < second.right() && first.right() > second.left()
                && first.top() < second.bottom() && first.bottom() > second.top();
    }
}
