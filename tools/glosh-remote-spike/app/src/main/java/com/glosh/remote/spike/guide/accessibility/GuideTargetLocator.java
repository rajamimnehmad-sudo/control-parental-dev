package com.glosh.remote.spike.guide.accessibility;

import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.wizard.OemFamily;

import java.util.ArrayList;
import java.util.List;

public final class GuideTargetLocator {
    public record LocatedTarget(GuideStage stage, NodeSnapshot node) {
    }

    private final TargetMatcher matcher;

    public GuideTargetLocator(TargetMatcher matcher) {
        this.matcher = matcher;
    }

    public LocatedTarget locate(
            SettingsSnapshot snapshot,
            OemFamily family,
            GuideStage currentStage,
            boolean rescue) {
        List<GuideStage> stages = new ArrayList<>();
        stages.add(currentStage);
        if (isDeveloperLearningStage(currentStage)) {
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

    private boolean isDeveloperLearningStage(GuideStage stage) {
        return stage == GuideStage.DEV_ABOUT_PHONE
                || stage == GuideStage.DEV_SOFTWARE_INFO
                || stage == GuideStage.DEV_BUILD_NUMBER;
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
