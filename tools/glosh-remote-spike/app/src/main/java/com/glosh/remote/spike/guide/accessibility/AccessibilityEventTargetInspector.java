package com.glosh.remote.spike.guide.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/** Reads an event source synchronously and never retains its live nodes. */
public final class AccessibilityEventTargetInspector {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_CANDIDATES = 50;

    private final TargetMatcher matcher;

    public AccessibilityEventTargetInspector(TargetMatcher matcher) {
        this.matcher = matcher;
    }

    public boolean matches(AccessibilityNodeInfo source, TargetSpec spec, String screenTitle) {
        if (source == null || spec == null) {
            return false;
        }
        List<TargetCandidate> candidates = new ArrayList<>();
        collect(source, screenTitle, 0, candidates);
        return matcher.best(spec, candidates).actionable();
    }

    private void collect(
            AccessibilityNodeInfo node,
            String screenTitle,
            int depth,
            List<TargetCandidate> candidates) {
        if (node == null || depth > MAX_DEPTH || candidates.size() >= MAX_CANDIDATES) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        AccessibilityNodeInfo parent = node.getParent();
        String parentText;
        try {
            parentText = parent == null ? "" : string(parent.getText());
        } finally {
            if (parent != null) {
                parent.recycle();
            }
        }
        candidates.add(new TargetCandidate(
                string(node.getText()),
                string(node.getContentDescription()),
                node.getViewIdResourceName(),
                screenTitle,
                parentText,
                "",
                string(node.getClassName()),
                node.isClickable(),
                bounds));
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collect(child, screenTitle, depth + 1, candidates);
            } finally {
                child.recycle();
            }
        }
    }

    private String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
