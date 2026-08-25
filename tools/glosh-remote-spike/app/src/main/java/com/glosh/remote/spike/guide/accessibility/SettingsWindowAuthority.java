package com.glosh.remote.spike.guide.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SettingsWindowAuthority {
    private final AccessibilityService service;
    private final SettingsWindowSelector selector = new SettingsWindowSelector();
    private final SettingsTreeScanner scanner = new SettingsTreeScanner();

    public SettingsWindowAuthority(AccessibilityService service) {
        this.service = service;
    }

    public SettingsSnapshot capture(Set<String> trustedPackages) {
        SettingsWindowSelector.Selection selection = selector.select(
                describeWindows(), trustedPackages);
        if (!selection.selected()) {
            return null;
        }
        AccessibilityNodeInfo root = freshRoot(selection.window().id(), trustedPackages);
        if (root == null) {
            return null;
        }
        try {
            return scanner.scan(root, selection.window().id());
        } finally {
            root.recycle();
        }
    }

    public SettingsSnapshot recapture(int windowId, Set<String> trustedPackages) {
        AccessibilityNodeInfo root = freshRoot(windowId, trustedPackages);
        if (root == null) {
            return null;
        }
        try {
            return scanner.scan(root, windowId);
        } finally {
            root.recycle();
        }
    }

    public SettingsSnapshot snapshot(AccessibilityNodeInfo root, int windowId) {
        return root == null ? null : scanner.scan(root, windowId);
    }

    public AccessibilityNodeInfo freshRoot(int windowId, Set<String> trustedPackages) {
        for (AccessibilityWindowInfo window : service.getWindows()) {
            if (window.getId() != windowId
                    || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            String packageName = root == null || root.getPackageName() == null
                    ? ""
                    : root.getPackageName().toString();
            if (SettingsPackageResolver.isAllowed(packageName, trustedPackages)) {
                return root;
            }
            if (root != null) {
                root.recycle();
            }
        }
        return null;
    }

    public AccessibilityNodeInfo nodeAtPath(AccessibilityNodeInfo root, List<Integer> path) {
        AccessibilityNodeInfo current = root;
        for (int index : path) {
            if (current == null || index < 0 || index >= current.getChildCount()) {
                return null;
            }
            current = current.getChild(index);
        }
        return current;
    }

    public boolean performClickAtPath(
            int windowId,
            List<Integer> path,
            TargetCandidate expected,
            Set<String> trustedPackages) {
        AccessibilityNodeInfo root = freshRoot(windowId, trustedPackages);
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo current = root;
        try {
            for (int index : path) {
                if (index < 0 || index >= current.getChildCount()) {
                    return false;
                }
                AccessibilityNodeInfo next = current.getChild(index);
                if (next == null) {
                    return false;
                }
                if (current != root) {
                    current.recycle();
                }
                current = next;
            }
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);
            return expected != null
                    && current.isClickable()
                    && current.isEnabled()
                    && same(expected.text(), current.getText())
                    && same(expected.contentDescription(), current.getContentDescription())
                    && same(expected.viewId(), current.getViewIdResourceName())
                    && expected.bounds().equals(bounds)
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } finally {
            if (current != root) {
                current.recycle();
            }
            root.recycle();
        }
    }

    public boolean performScrollAtPath(
            int windowId,
            List<Integer> path,
            TargetCandidate expected,
            int action,
            Set<String> trustedPackages) {
        AccessibilityNodeInfo root = freshRoot(windowId, trustedPackages);
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo current = root;
        try {
            for (int index : path) {
                if (index < 0 || index >= current.getChildCount()) {
                    return false;
                }
                AccessibilityNodeInfo next = current.getChild(index);
                if (next == null) {
                    return false;
                }
                if (current != root) {
                    current.recycle();
                }
                current = next;
            }
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);
            boolean supported = current.getActionList().stream()
                    .anyMatch(value -> value.getId() == action);
            return expected != null
                    && current.isScrollable()
                    && current.isEnabled()
                    && supported
                    && same(expected.viewId(), current.getViewIdResourceName())
                    && expected.bounds().equals(bounds)
                    && current.performAction(action);
        } finally {
            if (current != root) {
                current.recycle();
            }
            root.recycle();
        }
    }

    private List<SettingsWindowSelector.WindowCandidate> describeWindows() {
        List<SettingsWindowSelector.WindowCandidate> candidates = new ArrayList<>();
        for (AccessibilityWindowInfo window : service.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            try {
                String packageName = root == null || root.getPackageName() == null
                        ? ""
                        : root.getPackageName().toString();
                candidates.add(new SettingsWindowSelector.WindowCandidate(
                        window.getId(), mapType(window.getType()), packageName,
                        window.isActive(), window.isFocused()));
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        }
        return candidates;
    }

    private SettingsWindowSelector.WindowType mapType(int type) {
        if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return SettingsWindowSelector.WindowType.APPLICATION;
        }
        if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return SettingsWindowSelector.WindowType.ACCESSIBILITY_OVERLAY;
        }
        if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return SettingsWindowSelector.WindowType.INPUT_METHOD;
        }
        return SettingsWindowSelector.WindowType.OTHER;
    }

    private boolean same(String expected, CharSequence actual) {
        String left = expected == null ? "" : expected;
        String right = actual == null ? "" : actual.toString();
        return left.equals(right);
    }
}
