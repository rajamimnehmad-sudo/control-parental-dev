package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.TargetCandidate;
import com.glosh.remote.spike.guide.accessibility.TargetMatcher;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Confidence;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.ClassifiedScreen;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.MatchedTarget;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.TargetKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SamsungSettingsClassifier {
    private static final Set<String> ABOUT_PHONE = aliases(
            "Acerca del teléfono", "About phone");
    private static final Set<String> SOFTWARE_INFO = aliases(
            "Información de software", "Software information");
    private static final Set<String> BUILD_NUMBER = aliases(
            "Número de compilación", "Build number");
    private static final Set<String> DEVELOPER_OPTIONS = aliases(
            "Opciones de desarrollador", "Developer options");
    private static final Set<String> WIRELESS_DEBUGGING = aliases(
            "Depuración inalámbrica", "Wireless debugging");
    private static final Set<String> PAIR_WITH_CODE = aliases(
            "Vincular dispositivo con código de vinculación",
            "Vincular dispositivo con un código de vinculación",
            "Vincular dispositivo con código",
            "Emparejar dispositivo con código",
            "Pair device with pairing code");
    private static final Set<String> SETTINGS_HOME = aliases("Ajustes", "Settings");
    private static final Set<String> CREDENTIAL_TITLES = aliases(
            "Confirmar PIN", "Confirmá tu PIN", "Introducir PIN",
            "Confirm password", "Confirmar contraseña", "Confirm pattern",
            "Confirmar patrón");
    private static final Set<String> NETWORK_CONFIRM_TITLES = aliases(
            "¿Permitir depuración inalámbrica en esta red?",
            "Permitir depuración inalámbrica en esta red",
            "Allow wireless debugging on this network?");
    private static final Set<String> NETWORK_CONFIRM_POSITIVE = aliases(
            "Permitir", "Allow");

    public ClassifiedScreen classify(SettingsSnapshot snapshot) {
        String title = normalize(snapshot.screenTitle());
        Screen screen = screen(snapshot, title);
        EnumMap<TargetKey, MatchedTarget> targets = new EnumMap<>(TargetKey.class);

        put(targets, match(snapshot, screen == Screen.ABOUT_PHONE,
                TargetKey.SOFTWARE_INFO, SOFTWARE_INFO, false, false));
        put(targets, match(snapshot, screen == Screen.SOFTWARE_INFO,
                TargetKey.BUILD_NUMBER, BUILD_NUMBER, false, false));
        put(targets, match(snapshot, screen == Screen.DEVELOPER_OPTIONS,
                TargetKey.WIRELESS_DEBUGGING, WIRELESS_DEBUGGING, false, false));
        put(targets, matchWirelessToggle(snapshot, screen == Screen.WIRELESS_DEBUGGING));
        put(targets, match(snapshot, screen == Screen.WIRELESS_DEBUGGING,
                TargetKey.PAIR_WITH_CODE, PAIR_WITH_CODE, false, false));
        put(targets, match(snapshot, screen == Screen.NETWORK_CONFIRMATION,
                TargetKey.NETWORK_CONFIRM_POSITIVE, NETWORK_CONFIRM_POSITIVE, false, false));

        MatchedTarget toggle = targets.get(TargetKey.WIRELESS_DEBUGGING_TOGGLE);
        Boolean wirelessEnabled = screen == Screen.WIRELESS_DEBUGGING
                ? wirelessState(snapshot)
                : null;
        boolean policyBlocked = screen == Screen.WIRELESS_DEBUGGING
                && toggle != null
                && !toggle.node().enabled();
        Confidence confidence = screen == Screen.UNKNOWN ? Confidence.LOW : Confidence.HIGH;
        return new ClassifiedScreen(screen, confidence, targets, wirelessEnabled, policyBlocked);
    }

    private Screen screen(SettingsSnapshot snapshot, String title) {
        if (contains(CREDENTIAL_TITLES, title)) {
            return Screen.CREDENTIAL_PROMPT;
        }
        if (contains(NETWORK_CONFIRM_TITLES, title)
                && hasExact(snapshot, NETWORK_CONFIRM_POSITIVE)) {
            return Screen.NETWORK_CONFIRMATION;
        }
        boolean pairingContext = snapshot.visibleText().stream()
                .map(value -> normalize(value.value()))
                .anyMatch(value -> value.contains("codigo de vinculacion")
                        || value.contains("pairing code"));
        boolean sixDigits = snapshot.visibleText().stream()
                .map(value -> value.value() == null ? "" : value.value().trim())
                .anyMatch(value -> value.matches("[0-9]{6}"));
        if (pairingContext && sixDigits) {
            return Screen.PAIRING_DIALOG;
        }
        if (contains(WIRELESS_DEBUGGING, title)) {
            return Screen.WIRELESS_DEBUGGING;
        }
        if (contains(DEVELOPER_OPTIONS, title)) {
            return Screen.DEVELOPER_OPTIONS;
        }
        if (contains(SOFTWARE_INFO, title)) {
            return Screen.SOFTWARE_INFO;
        }
        if (contains(ABOUT_PHONE, title)) {
            return Screen.ABOUT_PHONE;
        }
        if (contains(SETTINGS_HOME, title)) {
            return Screen.SETTINGS_HOME;
        }
        return Screen.UNKNOWN;
    }

    private MatchedTarget match(
            SettingsSnapshot snapshot,
            boolean expectedScreen,
            TargetKey key,
            Set<String> labels,
            boolean requireCheckable,
            boolean stableViewIdOnly) {
        if (!expectedScreen) {
            return null;
        }
        List<Scored> scored = new ArrayList<>();
        for (NodeSnapshot node : snapshot.nodes()) {
            TargetCandidate candidate = node.candidate();
            boolean own = exact(labels, candidate.text())
                    || exact(labels, candidate.contentDescription());
            boolean descendant = node.descendantTexts().stream().anyMatch(value -> exact(labels, value));
            boolean stableView = key == TargetKey.WIRELESS_DEBUGGING_TOGGLE
                    && endsWith(candidate.viewId(), "switch_widget");
            if ((!own && !descendant && !stableView)
                    || (requireCheckable && !node.checkable())
                    || (stableViewIdOnly && !stableView)) {
                continue;
            }
            int score = own ? 70 : descendant ? 68 : 70;
            if (candidate.clickable()) score += 15;
            if (node.enabled()) score += 5;
            if (endsWith(candidate.viewId(), "title")) score += 3;
            if (node.checkable()) score += 7;
            scored.add(new Scored(node, score));
        }
        scored.sort((left, right) -> Integer.compare(right.score(), left.score()));
        if (scored.isEmpty()) {
            return null;
        }
        Scored top = scored.get(0);
        Integer second = scored.size() > 1 ? scored.get(1).score() : null;
        boolean unique = scored.stream().filter(value -> value.score() == top.score()).count() == 1;
        boolean margin = second == null || top.score() - second >= 8;
        Confidence confidence = top.score() >= 85 && unique && margin
                ? Confidence.HIGH
                : Confidence.MEDIUM;
        return new MatchedTarget(
                key, top.node(), confidence, top.score(), second,
                top.node().candidate().clickable(), unique, margin);
    }

    private MatchedTarget matchWirelessToggle(SettingsSnapshot snapshot, boolean expectedScreen) {
        if (!expectedScreen || wirelessState(snapshot) == null) {
            return null;
        }
        List<NodeSnapshot> clickableSwitchRows = snapshot.nodes().stream()
                .filter(node -> node.candidate().clickable() && node.enabled())
                .filter(node -> endsWith(node.candidate().viewId(), "switch_background"))
                .filter(node -> exact(WIRELESS_DEBUGGING, node.candidate().text())
                        || exact(WIRELESS_DEBUGGING, node.candidate().contentDescription()))
                .toList();
        if (clickableSwitchRows.size() != 1) {
            return null;
        }
        NodeSnapshot node = clickableSwitchRows.get(0);
        return new MatchedTarget(
                TargetKey.WIRELESS_DEBUGGING_TOGGLE,
                node,
                Confidence.HIGH,
                100,
                null,
                true,
                true,
                true);
    }

    private Boolean wirelessState(SettingsSnapshot snapshot) {
        List<NodeSnapshot> stateNodes = snapshot.nodes().stream()
                .filter(NodeSnapshot::visible)
                .filter(NodeSnapshot::checkable)
                .filter(node -> endsWith(node.candidate().viewId(), "switch_widget"))
                .toList();
        return stateNodes.size() == 1 ? stateNodes.get(0).checked() : null;
    }

    private boolean hasExact(SettingsSnapshot snapshot, Set<String> labels) {
        return snapshot.nodes().stream().anyMatch(node ->
                exact(labels, node.candidate().text())
                        || exact(labels, node.candidate().contentDescription())
                        || node.descendantTexts().stream().anyMatch(value -> exact(labels, value)));
    }

    private static void put(EnumMap<TargetKey, MatchedTarget> targets, MatchedTarget target) {
        if (target != null) {
            targets.put(target.key(), target);
        }
    }

    private static Set<String> aliases(String... values) {
        Set<String> aliases = new HashSet<>();
        for (String value : values) {
            aliases.add(normalize(value));
        }
        return Set.copyOf(aliases);
    }

    private static boolean exact(Set<String> values, String raw) {
        return values.contains(normalize(raw));
    }

    private static boolean contains(Set<String> values, String normalized) {
        return values.contains(normalized);
    }

    private static boolean endsWith(String value, String suffix) {
        return value != null
                && value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value) {
        return TargetMatcher.normalize(value);
    }

    private record Scored(NodeSnapshot node, int score) {
    }
}
