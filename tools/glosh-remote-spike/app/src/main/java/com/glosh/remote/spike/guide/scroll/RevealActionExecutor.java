package com.glosh.remote.spike.guide.scroll;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.guide.accessibility.GuideActionPolicy;
import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;
import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.SettingsWindowAuthority;

import java.util.Set;

public final class RevealActionExecutor {
    public enum Result {
        PERFORMED,
        STOPPED,
        STALE,
        UNSUPPORTED
    }

    private final SettingsWindowAuthority authority;
    private final RevealScrollController controller;
    private final ScanGenerationGuard guard;

    public RevealActionExecutor(
            SettingsWindowAuthority authority,
            RevealScrollController controller,
            ScanGenerationGuard guard) {
        this.authority = authority;
        this.controller = controller;
        this.guard = guard;
    }

    public Result perform(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            NodeSnapshot targetSnapshot,
            Rect visibleBounds,
            Set<String> trustedPackages) {
        if (!guard.isCurrent(token, snapshot)) {
            controller.cancel();
            return Result.STALE;
        }
        AccessibilityNodeInfo root = authority.freshRoot(snapshot.windowId(), trustedPackages);
        SettingsSnapshot fresh = root == null
                ? null
                : authority.snapshot(root, snapshot.windowId());
        if (fresh == null || !guard.isCurrent(token, fresh)) {
            controller.cancel();
            return Result.STALE;
        }
        AccessibilityNodeInfo target = targetSnapshot == null
                ? null
                : authority.nodeAtPath(root, targetSnapshot.path());
        AccessibilityNodeInfo scrollable = target == null
                ? findScrollableDescendant(root)
                : findScrollable(target);
        int showAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId();
        int downAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        int forwardAction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        RevealScrollController.Action action = controller.next(
                SystemClock.elapsedRealtime(),
                target != null,
                target != null && Rect.intersects(visibleBounds, targetSnapshot.candidate().bounds()),
                target != null && supports(target, showAction),
                scrollable != null && supports(scrollable, downAction),
                scrollable != null && supports(scrollable, forwardAction),
                fresh.fingerprint());
        AccessibilityNodeInfo receiver = action == RevealScrollController.Action.SHOW_ON_SCREEN
                ? target
                : scrollable;
        int frameworkAction = switch (action) {
            case SHOW_ON_SCREEN -> showAction;
            case SCROLL_DOWN -> downAction;
            case SCROLL_FORWARD -> forwardAction;
            case STOP -> 0;
        };
        GuideActionPolicy.Operation operation = switch (action) {
            case SHOW_ON_SCREEN -> GuideActionPolicy.Operation.SHOW_ON_SCREEN;
            case SCROLL_DOWN -> GuideActionPolicy.Operation.SCROLL_DOWN;
            case SCROLL_FORWARD -> GuideActionPolicy.Operation.SCROLL_FORWARD;
            case STOP -> null;
        };
        if (action == RevealScrollController.Action.STOP) {
            return Result.STOPPED;
        }
        if (receiver == null || frameworkAction == 0 || !GuideActionPolicy.isAllowed(operation)) {
            controller.cancel();
            return Result.UNSUPPORTED;
        }
        if (!receiver.performAction(frameworkAction)) {
            controller.cancel();
            return Result.UNSUPPORTED;
        }
        controller.performed(SystemClock.elapsedRealtime());
        return Result.PERFORMED;
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo start) {
        AccessibilityNodeInfo current = start;
        while (current != null) {
            if (current.isScrollable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollableDescendant(AccessibilityNodeInfo node) {
        if (node == null || node.isScrollable()) {
            return node;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findScrollableDescendant(node.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean supports(AccessibilityNodeInfo node, int action) {
        return node.getActionList().stream().anyMatch(value -> value.getId() == action);
    }
}
