package com.glosh.remote.spike.guide.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class SettingsTreeScanner {
    private static final int MAX_NODES = 500;

    public SettingsSnapshot scan(AccessibilityNodeInfo root, int windowId) {
        if (root == null) {
            return null;
        }
        String packageName = string(root.getPackageName());
        String title = findTitle(root);
        List<NodeSnapshot> nodes = new ArrayList<>();
        List<PairingCodeDetector.VisibleText> visibleText = new ArrayList<>();
        walk(root, title, List.of(), List.of(), nodes, visibleText);
        String fingerprint = fingerprint(title, nodes);
        return new SettingsSnapshot(
                windowId, packageName, title, fingerprint, nodes, visibleText);
    }

    private void walk(
            AccessibilityNodeInfo node,
            String title,
            List<Integer> path,
            List<String> ancestors,
            List<NodeSnapshot> nodes,
            List<PairingCodeDetector.VisibleText> visibleText) {
        if (node == null || nodes.size() >= MAX_NODES) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String parent = ancestors.isEmpty() ? "" : ancestors.get(ancestors.size() - 1);
        List<String> descendants = descendantTexts(node, 3, 24);
        String children = String.join(" ", descendants);
        TargetCandidate candidate = new TargetCandidate(
                string(node.getText()),
                string(node.getContentDescription()),
                node.getViewIdResourceName(),
                title,
                parent,
                children,
                string(node.getClassName()),
                node.isClickable(),
                bounds);
        nodes.add(new NodeSnapshot(
                path,
                candidate,
                node.isScrollable(),
                node.isCheckable(),
                node.isCheckable() ? node.isChecked() : null,
                node.isEnabled(),
                node.isVisibleToUser(),
                ancestors,
                descendants));
        if (!candidate.text().isEmpty()) {
            visibleText.add(new PairingCodeDetector.VisibleText(candidate.text(), parent, title));
        }
        if (!candidate.contentDescription().isEmpty()) {
            visibleText.add(new PairingCodeDetector.VisibleText(
                    candidate.contentDescription(), parent, title));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(index);
            List<String> childAncestors = new ArrayList<>(ancestors);
            String own = textOf(node);
            if (!own.isEmpty()) {
                childAncestors.add(own);
            }
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                walk(child, title, childPath, childAncestors, nodes, visibleText);
            } finally {
                child.recycle();
            }
        }
    }

    private String fingerprint(String title, List<NodeSnapshot> nodes) {
        StringBuilder logical = new StringBuilder(TargetMatcher.normalize(title));
        logical.append('|').append(nodes.size());
        for (NodeSnapshot node : nodes) {
            TargetCandidate candidate = node.candidate();
            Rect bounds = candidate.bounds();
            logical.append('|').append(node.path())
                    .append(':').append(TargetMatcher.normalize(candidate.text()))
                    .append(':').append(TargetMatcher.normalize(candidate.contentDescription()))
                    .append(':').append(candidate.viewId() == null ? "" : candidate.viewId())
                    .append(':').append(candidate.className())
                    .append(':').append(candidate.clickable())
                    .append(':').append(node.checkable())
                    .append(':').append(node.checked())
                    .append(':').append(node.enabled())
                    .append(':').append(node.visible())
                    .append(':').append(bounds.left).append(',').append(bounds.top)
                    .append(',').append(bounds.right).append(',').append(bounds.bottom);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(logical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private String findTitle(AccessibilityNodeInfo root) {
        String identified = findTitleById(root, false);
        return identified.isEmpty() ? firstVisibleText(root) : identified;
    }

    private String findTitleById(AccessibilityNodeInfo node, boolean insideTitleContainer) {
        if (node == null) {
            return "";
        }
        String own = SettingsTitleDetector.explicitTitle(
                node.getText(), node.getContentDescription());
        boolean titleContext = insideTitleContainer
                || SettingsTitleDetector.isTitleContainer(node.getViewIdResourceName());
        if (!own.isEmpty() && titleContext) {
            return own;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                String found = findTitleById(child, titleContext);
                if (!found.isEmpty()) {
                    return found;
                }
            } finally {
                child.recycle();
            }
        }
        return "";
    }

    private String firstVisibleText(AccessibilityNodeInfo root) {
        for (int index = 0; index < root.getChildCount(); index++) {
            AccessibilityNodeInfo child = root.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                String text = textOf(child);
                String viewId = child.getViewIdResourceName();
                if (!text.isEmpty() && (viewId == null
                        || viewId.contains("title")
                        || viewId.contains("collapsing_toolbar"))) {
                    return text;
                }
            } finally {
                child.recycle();
            }
        }
        return textOf(root);
    }

    private List<String> descendantTexts(AccessibilityNodeInfo node, int depth, int limit) {
        List<String> values = new ArrayList<>();
        collectDescendantTexts(node, depth, limit, values);
        return List.copyOf(values);
    }

    private void collectDescendantTexts(
            AccessibilityNodeInfo node, int depth, int limit, List<String> values) {
        if (node == null || depth <= 0 || values.size() >= limit) {
            return;
        }
        for (int index = 0; index < node.getChildCount() && values.size() < limit; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                String text = textOf(child);
                if (!text.isEmpty()) {
                    values.add(text);
                }
                collectDescendantTexts(child, depth - 1, limit, values);
            } finally {
                child.recycle();
            }
        }
    }

    private String textOf(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        String text = string(node.getText());
        return text.isEmpty() ? string(node.getContentDescription()) : text;
    }

    private String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
