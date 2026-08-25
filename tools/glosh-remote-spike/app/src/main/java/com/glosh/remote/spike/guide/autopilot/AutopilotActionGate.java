package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Confidence;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.MatchedTarget;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.TargetKey;

public final class AutopilotActionGate {
    public boolean authorize(
            ScanGenerationGuard.Token token,
            ScanGenerationGuard.Token currentToken,
            MatchedTarget target,
            TargetKey expectedKey) {
        return token != null
                && token.equals(currentToken)
                && target != null
                && target.key() == expectedKey
                && target.confidence() == Confidence.HIGH
                && target.clickable()
                && target.unique()
                && target.marginOk();
    }
}
