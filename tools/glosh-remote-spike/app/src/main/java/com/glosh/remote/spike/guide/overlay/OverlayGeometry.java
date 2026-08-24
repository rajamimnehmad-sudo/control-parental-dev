package com.glosh.remote.spike.guide.overlay;

import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;

public final class OverlayGeometry {
    private static final int MARGIN = 16;

    private OverlayGeometry() {
    }

    public record Box(int left, int top, int right, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }
    }

    public record EdgeInsets(int left, int top, int right, int bottom) {
    }

    public record Position(int x, int y) {
    }

    public static Box clamp(Box target, Box display, EdgeInsets insets) {
        int left = Math.max(target.left(), display.left() + insets.left());
        int top = Math.max(target.top(), display.top() + insets.top());
        int right = Math.min(target.right(), display.right() - insets.right());
        int bottom = Math.min(target.bottom(), display.bottom() - insets.bottom());
        return right > left && bottom > top ? new Box(left, top, right, bottom) : new Box(0, 0, 0, 0);
    }

    public static Position placeBubble(
            Box display, Box target, int width, int height, EdgeInsets insets) {
        int left = Math.max(display.left() + insets.left() + MARGIN,
                Math.min(target.centerX() - width / 2, display.right() - insets.right() - width - MARGIN));
        int above = target.top() - height - MARGIN;
        int below = target.bottom() + MARGIN;
        int top = above >= display.top() + insets.top() + MARGIN
                ? above
                : Math.min(below, display.bottom() - insets.bottom() - height - MARGIN);
        return new Position(left, Math.max(display.top() + insets.top() + MARGIN, top));
    }

    public static Rect clampHighlight(Rect target, Rect display, Insets insets) {
        Rect safe = new Rect(
                display.left + insets.left,
                display.top + insets.top,
                display.right - insets.right,
                display.bottom - insets.bottom);
        Rect result = new Rect(target);
        if (!result.intersect(safe)) {
            return new Rect();
        }
        return result;
    }

    public static Point bubblePosition(Rect display, Rect target, int width, int height, Insets insets) {
        int left = Math.max(display.left + insets.left + MARGIN,
                Math.min(target.centerX() - width / 2, display.right - insets.right - width - MARGIN));
        int above = target.top - height - MARGIN;
        int below = target.bottom + MARGIN;
        int top = above >= display.top + insets.top + MARGIN
                ? above
                : Math.min(below, display.bottom - insets.bottom - height - MARGIN);
        return new Point(left, Math.max(display.top + insets.top + MARGIN, top));
    }
}
