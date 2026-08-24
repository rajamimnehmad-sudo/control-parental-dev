package com.glosh.remote.spike.wizard;

import java.util.List;

public record OemGuideStep(
        String title,
        String body,
        List<String> rows,
        int highlightedRow,
        boolean showSevenTaps,
        RescueHelp help) {
}
