package com.glosh.remote.spike.guide.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;

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
        walk(root, title, List.of(), nodes, visibleText);
        String fingerprint = fingerprint(title, nodes);
        return new SettingsSnapshot(
                windowId, packageName, title, fingerprint, nodes, visibleText);
    }

    private void walk(
            AccessibilityNodeInfo node,
            String title,
            List<Integer> path,
            List<NodeSnapshot> nodes,
            List<PairingCodeDetector.VisibleText> visibleText) {
        if (node == null || nodes.size() >= MAX_NODES) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String parent = node.getParent() == null ? "" : textOf(node.getParent());
        String children = childText(node);
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
        nodes.add(new NodeSnapshot(path, candidate, node.isScrollable()));
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
            walk(node.getChild(index), title, childPath, nodes, visibleText);
        }
    }

    private String fingerprint(String title, List<NodeSnapshot> nodes) {
        StringBuilder logical = new StringBuilder(TargetMatcher.normalize(title));
        logical.append('|').append(nodes.size());
        for (NodeSnapshot node : nodes) {
            TargetCandidate candidate = node.candidate();
            if (!candidate.text().isEmpty() || !candidate.contentDescription().isEmpty()) {
                logical.append('|')
                        .append(TargetMatcher.normalize(candidate.text()))
                        .append(':')
                        .append(TargetMatcher.normalize(candidate.contentDescription()))
                        .append(':')
                        .append(candidate.viewId() == null ? "" : candidate.viewId());
            }
        }
        return Integer.toHexString(logical.toString().hashCode()) + ":" + logical.length();
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
            String found = findTitleById(node.getChild(index), titleContext);
            if (!found.isEmpty()) {
                return found;
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
            String text = textOf(child);
            String viewId = child.getViewIdResourceName();
            if (!text.isEmpty() && (viewId == null
                    || viewId.contains("title")
                    || viewId.contains("collapsing_toolbar"))) {
                return text;
            }
        }
        return textOf(root);
    }

    private String childText(AccessibilityNodeInfo node) {
        StringBuilder value = new StringBuilder();
        int limit = Math.min(node.getChildCount(), 4);
        for (int index = 0; index < limit; index++) {
            String text = textOf(node.getChild(index));
            if (!text.isEmpty()) {
                if (value.length() > 0) {
                    value.append(' ');
                }
                value.append(text);
            }
        }
        return value.toString();
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
