package com.glosh.remote.spike.guide.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;

import java.util.ArrayList;
import java.util.List;

public final class SettingsTreeScanner {
    private static final int MAX_NODES = 500;

    public record NodeRecord(AccessibilityNodeInfo node, TargetCandidate candidate) {
    }

    public record ScanResult(
            String screenTitle,
            List<NodeRecord> nodes,
            List<PairingCodeDetector.VisibleText> visibleText) {
    }

    public ScanResult scan(AccessibilityNodeInfo root) {
        if (root == null) {
            return new ScanResult("", List.of(), List.of());
        }
        String title = findTitle(root);
        List<NodeRecord> nodes = new ArrayList<>();
        List<PairingCodeDetector.VisibleText> text = new ArrayList<>();
        walk(root, title, nodes, text);
        return new ScanResult(title, List.copyOf(nodes), List.copyOf(text));
    }

    private void walk(
            AccessibilityNodeInfo node,
            String title,
            List<NodeRecord> nodes,
            List<PairingCodeDetector.VisibleText> text) {
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
        nodes.add(new NodeRecord(node, candidate));
        if (!candidate.text().isEmpty()) {
            text.add(new PairingCodeDetector.VisibleText(candidate.text(), parent, title));
        }
        if (!candidate.contentDescription().isEmpty()) {
            text.add(new PairingCodeDetector.VisibleText(candidate.contentDescription(), parent, title));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            walk(node.getChild(index), title, nodes, text);
        }
    }

    private String findTitle(AccessibilityNodeInfo root) {
        String identified = findTitleById(root);
        if (!identified.isEmpty()) {
            return identified;
        }
        return firstVisibleText(root);
    }

    private String findTitleById(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        String own = textOf(node);
        String viewId = node.getViewIdResourceName();
        if (!own.isEmpty() && viewId != null
                && (viewId.contains("collapsing_toolbar")
                || viewId.contains("toolbar_title")
                || viewId.contains("action_bar_title"))) {
            return own;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            String found = findTitleById(node.getChild(index));
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
            AccessibilityNodeInfo child = node.getChild(index);
            String text = textOf(child);
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
