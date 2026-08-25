package com.glosh.remote.spike.guide.accessibility;

import com.glosh.remote.spike.guide.autopilot.AutopilotContract;
import com.glosh.remote.spike.guide.autopilot.SamsungSettingsClassifier;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.wizard.OemFamily;

import java.util.ArrayList;
import java.util.List;

public final class GuideTargetLocator {
    public record LocatedTarget(GuideStage stage, NodeSnapshot node) {
    }

    private final TargetMatcher matcher;
    private final SamsungSettingsClassifier samsungClassifier = new SamsungSettingsClassifier();

    public GuideTargetLocator(TargetMatcher matcher) {
        this.matcher = matcher;
    }

    public LocatedTarget locate(
            SettingsSnapshot snapshot,
            OemFamily family,
            GuideStage currentStage,
            boolean rescue) {
        GuideStage landed = landedSamsungStage(snapshot, family, currentStage);
        if (landed != null && landed != currentStage) {
            // The service only needs the stage transition here. No target is consumed until the
            // next stable snapshot, so a null node is intentional and safe.
            return new LocatedTarget(landed, null);
        }

        List<GuideStage> stages = new ArrayList<>();
        stages.add(currentStage);
        if (isDeveloperLearningStage(currentStage)) {
            GuideStage next = nextDeveloperStage(currentStage);
            if (next != null) {
                stages.add(next);
            }
            stages.add(GuideStage.WIRELESS_DEBUGGING);
        }
        if (rescue) {
            for (GuideStage stage : observableStages()) {
                if (!stages.contains(stage)) {
                    stages.add(stage);
                }
            }
        }
        List<TargetCandidate> candidates = new ArrayList<>();
        for (NodeSnapshot node : snapshot.nodes()) {
            candidates.add(node.candidate());
        }
        for (GuideStage stage : stages) {
            TargetSpec spec = GuideTargetCatalog.forStage(family, stage);
            if (spec == null) {
                continue;
            }
            TargetMatcher.Match match = matcher.best(spec, candidates);
            if (!match.actionable()) {
                continue;
            }
            for (NodeSnapshot node : snapshot.nodes()) {
                if (node.candidate().equals(match.candidate())) {
                    return new LocatedTarget(stage, node);
                }
            }
        }
        return null;
    }

    public boolean isExpectedScreen(OemFamily family, GuideStage stage, String screenTitle) {
        TargetSpec spec = GuideTargetCatalog.forStage(family, stage);
        if (spec == null || spec.screenTitles().isEmpty()) {
            return false;
        }
        String normalized = TargetMatcher.normalize(screenTitle);
        return spec.screenTitles().stream()
                .map(TargetMatcher::normalize)
                .anyMatch(normalized::equals);
    }

    private GuideStage landedSamsungStage(
            SettingsSnapshot snapshot,
            OemFamily family,
            GuideStage currentStage) {
        if (family != OemFamily.SAMSUNG) {
            return null;
        }
        AutopilotContract.Screen screen = samsungClassifier.classify(snapshot).screen();
        if (currentStage == GuideStage.DEV_ABOUT_PHONE
                && screen == AutopilotContract.Screen.ABOUT_PHONE) {
            return GuideStage.DEV_SOFTWARE_INFO;
        }
        if ((currentStage == GuideStage.DEV_ABOUT_PHONE
                || currentStage == GuideStage.DEV_SOFTWARE_INFO)
                && screen == AutopilotContract.Screen.SOFTWARE_INFO) {
            return GuideStage.DEV_BUILD_NUMBER;
        }
        if (isDeveloperLearningStage(currentStage)
                && screen == AutopilotContract.Screen.WIRELESS_DEBUGGING) {
            return GuideStage.WIRELESS_DEBUGGING;
        }
        return null;
    }

    private boolean isDeveloperLearningStage(GuideStage stage) {
        return stage == GuideStage.DEV_ABOUT_PHONE
                || stage == GuideStage.DEV_SOFTWARE_INFO
                || stage == GuideStage.DEV_BUILD_NUMBER;
    }

    private GuideStage nextDeveloperStage(GuideStage stage) {
        return switch (stage) {
            case DEV_ABOUT_PHONE -> GuideStage.DEV_SOFTWARE_INFO;
            case DEV_SOFTWARE_INFO -> GuideStage.DEV_BUILD_NUMBER;
            default -> null;
        };
    }

    private List<GuideStage> observableStages() {
        return List.of(
                GuideStage.DEV_ABOUT_PHONE,
                GuideStage.DEV_SOFTWARE_INFO,
                GuideStage.DEV_BUILD_NUMBER,
                GuideStage.WIRELESS_DEBUGGING,
                GuideStage.PAIR_CODE_TARGET);
    }
}
