package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.SettingsWindowAuthority;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.ClassifiedScreen;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.MatchedTarget;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.TargetKey;

import java.util.Set;

public final class FreshNodeClickExecutor {
    public enum Result { CLICKED, STALE, REJECTED, ACTION_FAILED }

    private final SettingsWindowAuthority authority;
    private final ScanGenerationGuard generationGuard;
    private final SamsungSettingsClassifier classifier;
    private final AutopilotActionGate actionGate;

    public FreshNodeClickExecutor(
            SettingsWindowAuthority authority,
            ScanGenerationGuard generationGuard,
            SamsungSettingsClassifier classifier,
            AutopilotActionGate actionGate) {
        this.authority = authority;
        this.generationGuard = generationGuard;
        this.classifier = classifier;
        this.actionGate = actionGate;
    }

    public Result click(
            ScanGenerationGuard.Token originalToken,
            SettingsSnapshot originalSnapshot,
            TargetKey expectedKey,
            Set<String> trustedPackages) {
        if (!generationGuard.isCurrent(originalToken, originalSnapshot)) {
            return Result.STALE;
        }
        SettingsSnapshot fresh = authority.recapture(originalSnapshot.windowId(), trustedPackages);
        if (!generationGuard.isCurrent(originalToken, fresh)) {
            return Result.STALE;
        }
        ClassifiedScreen classified = classifier.classify(fresh);
        MatchedTarget target = classified.target(expectedKey);
        ScanGenerationGuard.Token current = generationGuard.token(fresh);
        if (!actionGate.authorize(originalToken, current, target, expectedKey)) {
            return Result.REJECTED;
        }
        boolean clicked = authority.performClickAtPath(
                fresh.windowId(), target.node().path(), target.node().candidate(), trustedPackages);
        return clicked ? Result.CLICKED : Result.ACTION_FAILED;
    }
}
