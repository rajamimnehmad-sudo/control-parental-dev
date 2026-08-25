package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.TargetMatcher;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContextualPairingCodeDetector {
    public sealed interface Result permits Unique, Rejected {
    }

    public record Unique(String code) implements Result {
    }

    public record Rejected(String reason) implements Result {
    }

    private static final Pattern CODE = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");
    private static final List<String> CONTEXT = List.of(
            "código", "code", "vinculación", "vincular", "pairing",
            "depuración inalámbrica", "wireless debugging");

    public Result detect(SettingsSnapshot snapshot, Screen screen) {
        if (screen != Screen.PAIRING_DIALOG) {
            return new Rejected("wrong_screen");
        }
        boolean contextual = snapshot.visibleText().stream().anyMatch(value ->
                hasContext(value.value()) || hasContext(value.parent()) || hasContext(value.screenTitle()));
        if (!contextual) {
            return new Rejected("missing_pairing_context");
        }
        List<String> codes = snapshot.visibleText().stream()
                .flatMap(value -> matches(value.value()).stream())
                .distinct()
                .toList();
        if (codes.size() == 1) {
            return new Unique(codes.get(0));
        }
        return new Rejected(codes.isEmpty() ? "no_code" : "ambiguous_codes");
    }

    private List<String> matches(String raw) {
        Matcher matcher = CODE.matcher(raw == null ? "" : raw);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private boolean hasContext(String raw) {
        String normalized = TargetMatcher.normalize(raw);
        return CONTEXT.stream()
                .map(TargetMatcher::normalize)
                .anyMatch(normalized::contains);
    }
}
