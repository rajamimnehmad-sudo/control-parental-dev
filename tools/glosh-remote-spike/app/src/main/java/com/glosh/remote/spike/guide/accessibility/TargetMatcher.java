package com.glosh.remote.spike.guide.accessibility;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TargetMatcher {
    public enum Confidence {
        NONE,
        LOW,
        MEDIUM,
        HIGH
    }

    public record Match(TargetCandidate candidate, Confidence confidence, boolean ambiguous) {
        public boolean actionable() {
            return confidence == Confidence.HIGH && !ambiguous && candidate != null;
        }
    }

    public Match best(TargetSpec spec, List<TargetCandidate> candidates) {
        List<TargetCandidate> highs = new ArrayList<>();
        TargetCandidate best = null;
        Confidence bestConfidence = Confidence.NONE;
        for (TargetCandidate candidate : candidates) {
            Confidence confidence = score(spec, candidate);
            if (confidence == Confidence.HIGH) {
                highs.add(candidate);
            }
            if (confidence.ordinal() > bestConfidence.ordinal()) {
                best = candidate;
                bestConfidence = confidence;
            }
        }
        if (highs.size() == 1) {
            return new Match(highs.get(0), Confidence.HIGH, false);
        }
        if (highs.size() > 1) {
            return new Match(null, Confidence.HIGH, true);
        }
        return new Match(best, bestConfidence, false);
    }

    public Confidence score(TargetSpec spec, TargetCandidate candidate) {
        String text = normalize(candidate.text());
        String description = normalize(candidate.contentDescription());
        boolean exact = contains(spec.exactLabels(), text) || contains(spec.exactLabels(), description);
        boolean alias = contains(spec.aliases(), text) || contains(spec.aliases(), description);
        boolean viewId = suffixMatch(spec.viewIds(), candidate.viewId());
        boolean screen = spec.screenTitles().isEmpty()
                || contains(spec.screenTitles(), normalize(candidate.screenTitle()));
        boolean parent = contains(spec.parentContexts(), normalize(candidate.parentText()));
        boolean child = contains(spec.childContexts(), normalize(candidate.childText()));
        boolean role = spec.classNames().isEmpty() || suffixMatch(spec.classNames(), candidate.className());
        boolean clickable = !spec.requireClickable() || candidate.clickable();

        if (!screen || !role || !clickable) {
            return Confidence.NONE;
        }
        if (viewId && (exact || alias)) {
            return Confidence.HIGH;
        }
        if (exact && !spec.screenTitles().isEmpty()) {
            return Confidence.HIGH;
        }
        if (alias && (!spec.screenTitles().isEmpty()) && (parent || child)) {
            return Confidence.HIGH;
        }
        if (exact || viewId) {
            return Confidence.MEDIUM;
        }
        if (alias) {
            return Confidence.LOW;
        }
        return Confidence.NONE;
    }

    public static String normalize(CharSequence raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw.toString(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean contains(List<String> values, String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (normalize(value).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean suffixMatch(List<String> values, String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String normalized = normalize(raw);
        for (String value : values) {
            if (normalized.endsWith(normalize(value))) {
                return true;
            }
        }
        return false;
    }
}
