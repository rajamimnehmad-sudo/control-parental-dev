package com.glosh.remote.spike.guide.autopilot;

import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;
import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.SettingsWindowAuthority;

import java.util.List;
import java.util.Set;

/** Performs one semantic scroll transaction on one unique Settings container. */
public final class FreshSettingsScrollExecutor {
    public enum Result { SCROLLED, STALE, AMBIGUOUS, UNSUPPORTED }

    private final SettingsWindowAuthority authority;
    private final ScanGenerationGuard guard;
    private final SamsungSettingsClassifier classifier;

    public FreshSettingsScrollExecutor(
            SettingsWindowAuthority authority,
            ScanGenerationGuard guard,
            SamsungSettingsClassifier classifier) {
        this.authority = authority;
        this.guard = guard;
        this.classifier = classifier;
    }

    public Result scrollForward(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            AutopilotContract.Screen expectedScreen,
            Set<String> trustedPackages) {
        if (!guard.isCurrent(token, snapshot)) {
            return Result.STALE;
        }
        SettingsSnapshot fresh = authority.recapture(snapshot.windowId(), trustedPackages);
        if (!guard.isCurrent(token, fresh)
                || classifier.classify(fresh).screen() != expectedScreen) {
            return Result.STALE;
        }
        List<NodeSnapshot> scrollables = fresh.nodes().stream()
                .filter(NodeSnapshot::visible)
                .filter(NodeSnapshot::enabled)
                .filter(NodeSnapshot::scrollable)
                .toList();
        if (scrollables.size() != 1) {
            return Result.AMBIGUOUS;
        }
        NodeSnapshot receiver = scrollables.get(0);
        int down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        if (authority.performScrollAtPath(
                fresh.windowId(), receiver.path(), receiver.candidate(), down, trustedPackages)) {
            return Result.SCROLLED;
        }
        int forward = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        return authority.performScrollAtPath(
                fresh.windowId(), receiver.path(), receiver.candidate(), forward, trustedPackages)
                ? Result.SCROLLED
                : Result.UNSUPPORTED;
    }
}
