package com.glosh.remote.spike.guide.pairing;

import com.glosh.remote.spike.guide.accessibility.TargetMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PairingCodeDetector {
    private static final Pattern CODE = Pattern.compile("^[0-9]{6}$");
    private static final List<String> CONTEXT = List.of(
            "codigo de vinculacion",
            "codigo de emparejamiento",
            "pairing code",
            "emparejar dispositivo",
            "pair device",
            "depuracion inalambrica",
            "wireless debugging",
            "wi-fi pairing code");

    public record VisibleText(String value, String parent, String screenTitle) {
    }

    public String detect(List<VisibleText> values, boolean expectedPairingScreen) {
        if (!expectedPairingScreen || values == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        boolean screenContext = false;
        for (VisibleText value : values) {
            screenContext |= contextual(value.value())
                    || contextual(value.screenTitle())
                    || contextual(value.parent());
        }
        if (!screenContext) {
            return null;
        }
        for (VisibleText value : values) {
            String raw = value.value() == null ? "" : value.value().trim();
            if (CODE.matcher(raw).matches()
                    && (screenContext || contextual(value.parent()))) {
                candidates.add(raw);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private boolean contextual(String raw) {
        String normalized = TargetMatcher.normalize(raw);
        for (String term : CONTEXT) {
            if (normalized.contains(TargetMatcher.normalize(term))) {
                return true;
            }
        }
        return false;
    }
}
