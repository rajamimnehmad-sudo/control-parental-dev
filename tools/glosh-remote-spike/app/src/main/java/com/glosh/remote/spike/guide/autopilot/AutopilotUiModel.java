package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class AutopilotUiModel {
    public enum TargetKey {
        SOFTWARE_INFO("software_info"),
        BUILD_NUMBER("build_number"),
        WIRELESS_DEBUGGING("wireless_debugging"),
        WIRELESS_DEBUGGING_TOGGLE("wireless_debugging_toggle"),
        PAIR_WITH_CODE("pair_with_code"),
        NETWORK_CONFIRM_POSITIVE("network_confirm_positive");

        private final String plannerKey;

        TargetKey(String plannerKey) {
            this.plannerKey = plannerKey;
        }

        public String plannerKey() {
            return plannerKey;
        }
    }

    public record MatchedTarget(
            TargetKey key,
            NodeSnapshot node,
            AutopilotContract.Confidence confidence,
            int score,
            Integer runnerUpScore,
            boolean clickable,
            boolean unique,
            boolean marginOk) {
    }

    public record ClassifiedScreen(
            AutopilotContract.Screen screen,
            AutopilotContract.Confidence confidence,
            Map<TargetKey, MatchedTarget> targets,
            Boolean wirelessEnabled,
            boolean policyBlocked) {
        public ClassifiedScreen {
            EnumMap<TargetKey, MatchedTarget> copy = new EnumMap<>(TargetKey.class);
            if (targets != null) {
                copy.putAll(targets);
            }
            targets = Collections.unmodifiableMap(copy);
        }

        public MatchedTarget target(TargetKey key) {
            return targets.get(key);
        }
    }

    private AutopilotUiModel() {
    }
}
