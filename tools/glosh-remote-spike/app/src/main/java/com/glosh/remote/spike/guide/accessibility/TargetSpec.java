package com.glosh.remote.spike.guide.accessibility;

import java.util.List;

public record TargetSpec(
        List<String> exactLabels,
        List<String> aliases,
        List<String> screenTitles,
        List<String> parentContexts,
        List<String> childContexts,
        List<String> viewIds,
        List<String> classNames,
        boolean requireClickable) {
}
