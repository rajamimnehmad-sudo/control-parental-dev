package com.glosh.remote.spike.guide.accessibility;

final class GuideDebugSummary {
    private final TargetMatcher matcher;

    GuideDebugSummary(TargetMatcher matcher) {
        this.matcher = matcher;
    }

    String confidence(SettingsSnapshot snapshot, TargetSpec spec) {
        if (spec == null) {
            return "none";
        }
        int high = 0;
        int textLabel = 0;
        int descriptionLabel = 0;
        int textPrefix = 0;
        int descriptionPrefix = 0;
        int textContains = 0;
        int descriptionContains = 0;
        for (NodeSnapshot node : snapshot.nodes()) {
            TargetCandidate candidate = node.candidate();
            if (matchesLabel(spec, candidate.text())) textLabel++;
            if (matchesLabel(spec, candidate.contentDescription())) descriptionLabel++;
            if (startsWithLabel(spec, candidate.text())) textPrefix++;
            if (startsWithLabel(spec, candidate.contentDescription())) descriptionPrefix++;
            if (containsLabel(spec, candidate.text())) textContains++;
            if (containsLabel(spec, candidate.contentDescription())) descriptionContains++;
            if (matcher.score(spec, candidate) == TargetMatcher.Confidence.HIGH) high++;
        }
        return "high:" + high + ",text:" + textLabel + ",desc:" + descriptionLabel
                + ",textPrefix:" + textPrefix + ",descPrefix:" + descriptionPrefix
                + ",textContains:" + textContains + ",descContains:" + descriptionContains;
    }

    private boolean matchesLabel(TargetSpec spec, String value) {
        String normalized = TargetMatcher.normalize(value);
        if (normalized.isEmpty()) return false;
        return spec.exactLabels().stream().map(TargetMatcher::normalize).anyMatch(normalized::equals)
                || spec.aliases().stream().map(TargetMatcher::normalize).anyMatch(normalized::equals);
    }

    private boolean startsWithLabel(TargetSpec spec, String value) {
        String normalized = TargetMatcher.normalize(value);
        if (normalized.isEmpty()) return false;
        return spec.exactLabels().stream().map(TargetMatcher::normalize)
                .anyMatch(label -> startsWith(normalized, label))
                || spec.aliases().stream().map(TargetMatcher::normalize)
                .anyMatch(label -> startsWith(normalized, label));
    }

    private boolean containsLabel(TargetSpec spec, String value) {
        String normalized = TargetMatcher.normalize(value);
        if (normalized.isEmpty()) return false;
        return spec.exactLabels().stream().map(TargetMatcher::normalize).anyMatch(normalized::contains)
                || spec.aliases().stream().map(TargetMatcher::normalize).anyMatch(normalized::contains);
    }

    private boolean startsWith(String value, String label) {
        return value.startsWith(label + " ") || value.startsWith(label + ",");
    }
}
