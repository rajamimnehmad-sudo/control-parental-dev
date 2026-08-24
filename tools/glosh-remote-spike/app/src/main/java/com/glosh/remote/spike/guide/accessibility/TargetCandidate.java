package com.glosh.remote.spike.guide.accessibility;

import android.graphics.Rect;

public record TargetCandidate(
        String text,
        String contentDescription,
        String viewId,
        String screenTitle,
        String parentText,
        String childText,
        String className,
        boolean clickable,
        Rect bounds) {
}
