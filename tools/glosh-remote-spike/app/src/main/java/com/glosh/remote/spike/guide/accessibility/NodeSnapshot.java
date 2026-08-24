package com.glosh.remote.spike.guide.accessibility;

import java.util.List;

public record NodeSnapshot(List<Integer> path, TargetCandidate candidate, boolean scrollable) {
    public NodeSnapshot {
        path = path == null ? List.of() : List.copyOf(path);
    }
}
