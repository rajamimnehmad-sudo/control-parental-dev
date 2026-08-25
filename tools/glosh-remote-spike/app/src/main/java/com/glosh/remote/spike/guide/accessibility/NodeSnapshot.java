package com.glosh.remote.spike.guide.accessibility;

import java.util.List;

public record NodeSnapshot(
        List<Integer> path,
        TargetCandidate candidate,
        boolean scrollable,
        boolean checkable,
        Boolean checked,
        boolean enabled,
        boolean visible,
        List<String> ancestorTexts,
        List<String> descendantTexts) {
    public NodeSnapshot {
        path = path == null ? List.of() : List.copyOf(path);
        ancestorTexts = ancestorTexts == null ? List.of() : List.copyOf(ancestorTexts);
        descendantTexts = descendantTexts == null ? List.of() : List.copyOf(descendantTexts);
    }

    public NodeSnapshot(List<Integer> path, TargetCandidate candidate, boolean scrollable) {
        this(path, candidate, scrollable, false, null, true, true, List.of(), List.of());
    }
}
