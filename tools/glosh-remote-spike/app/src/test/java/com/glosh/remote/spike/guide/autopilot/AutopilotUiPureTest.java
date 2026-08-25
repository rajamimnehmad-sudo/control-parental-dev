package com.glosh.remote.spike.guide.autopilot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;
import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.TargetCandidate;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Confidence;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;
import com.glosh.remote.spike.guide.autopilot.AutopilotUiModel.TargetKey;
import com.glosh.remote.spike.guide.pairing.PairingCodeDetector.VisibleText;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class AutopilotUiPureTest {
    private final SamsungSettingsClassifier classifier = new SamsungSettingsClassifier();

    @Test
    public void samsungScreensTargetsAmbiguityAndPairCodeAreFailClosed() {
        SettingsSnapshot about = snapshot("Acerca del teléfono", List.of(
                clickableRow("Información de software", "software")));
        assertEquals(Screen.ABOUT_PHONE, classifier.classify(about).screen());
        assertEquals(Confidence.HIGH,
                classifier.classify(about).target(TargetKey.SOFTWARE_INFO).confidence());

        SettingsSnapshot software = snapshot("Información de software", List.of(
                clickableRow("Número de compilación", "build")));
        assertEquals(Screen.SOFTWARE_INFO, classifier.classify(software).screen());
        assertEquals(Confidence.HIGH,
                classifier.classify(software).target(TargetKey.BUILD_NUMBER).confidence());

        SettingsSnapshot developer = snapshot("Opciones de desarrollador", List.of(
                clickableRow("Depuración inalámbrica", "wireless")));
        assertEquals(Screen.DEVELOPER_OPTIONS, classifier.classify(developer).screen());
        assertEquals(Confidence.HIGH,
                classifier.classify(developer).target(TargetKey.WIRELESS_DEBUGGING).confidence());

        SettingsSnapshot wireless = snapshot("Depuración inalámbrica", List.of(
                toggle(false), toggleState(false), clickableRow(
                        "Vincular dispositivo con un código de vinculación", "pair")));
        assertEquals(Screen.WIRELESS_DEBUGGING, classifier.classify(wireless).screen());
        assertEquals(Boolean.FALSE, classifier.classify(wireless).wirelessEnabled());
        assertEquals(Confidence.HIGH,
                classifier.classify(wireless).target(TargetKey.PAIR_WITH_CODE).confidence());

        SettingsSnapshot duplicate = snapshot("Acerca del teléfono", List.of(
                clickableRow("Información de software", "a"),
                clickableRow("Información de software", "b")));
        assertNotEquals(Confidence.HIGH,
                classifier.classify(duplicate).target(TargetKey.SOFTWARE_INFO).confidence());

        SettingsSnapshot pairing = snapshot(
                "Depuración inalámbrica",
                List.of(clickableRow("Vincular dispositivo con código de vinculación", "pair")),
                List.of(
                        new VisibleText("Código de vinculación", "", "Depuración inalámbrica"),
                        new VisibleText("123456", "Código de vinculación", "Depuración inalámbrica")));
        assertEquals(Screen.PAIRING_DIALOG, classifier.classify(pairing).screen());
        assertEquals(new ContextualPairingCodeDetector.Unique("123456"),
                new ContextualPairingCodeDetector().detect(pairing, Screen.PAIRING_DIALOG));

        SettingsSnapshot ambiguousCode = snapshot(
                "Depuración inalámbrica",
                List.of(clickableRow("Vincular dispositivo con código de vinculación", "pair")),
                List.of(
                        new VisibleText("Código de vinculación", "", "Depuración inalámbrica"),
                        new VisibleText("123456", "", "Depuración inalámbrica"),
                        new VisibleText("654321", "", "Depuración inalámbrica")));
        assertTrue(new ContextualPairingCodeDetector().detect(
                ambiguousCode, Screen.PAIRING_DIALOG) instanceof ContextualPairingCodeDetector.Rejected);
    }

    @Test
    public void developerWirelessNavigationPrefersRowOverInternalSwitch() {
        SettingsSnapshot developer = snapshot("Opciones de desarrollador", List.of(
                clickableRow("Depuración inalámbrica", "wireless"),
                toggle(false),
                toggleState(false)));

        var target = classifier.classify(developer).target(TargetKey.WIRELESS_DEBUGGING);
        assertEquals(Confidence.HIGH, target.confidence());
        assertEquals("com.android.settings:id/wireless", target.node().candidate().viewId());
        assertFalse(target.node().checkable());
        assertFalse(target.node().candidate().className().contains("Switch"));
    }

    @Test
    public void developerWirelessNavigationResolvesDeepClickableAncestorByPath() {
        NodeSnapshot row = node(
                List.of(4),
                candidate("", "preference_row", true),
                false,
                null,
                List.of());
        NodeSnapshot container1 = node(
                List.of(4, 0), candidate("", "container_1", false), false, null, List.of());
        NodeSnapshot container2 = node(
                List.of(4, 0, 0), candidate("", "container_2", false), false, null, List.of());
        NodeSnapshot container3 = node(
                List.of(4, 0, 0, 0), candidate("", "container_3", false), false, null, List.of());
        NodeSnapshot title = node(
                List.of(4, 0, 0, 0, 0),
                new TargetCandidate(
                        "Depuración inalámbrica", "", "com.android.settings:id/title", "",
                        "", "", "android.widget.TextView", false,
                        new Rect(20, 100, 600, 160)),
                false,
                null,
                List.of());
        NodeSnapshot switchBackground = node(
                List.of(4, 1),
                new TargetCandidate(
                        "", "Depuración inalámbrica", "com.android.settings:id/switch_background",
                        "", "", "", "android.widget.Switch", true,
                        new Rect(700, 100, 900, 160)),
                false,
                null,
                List.of());
        NodeSnapshot switchWidget = new NodeSnapshot(
                List.of(4, 1, 0),
                new TargetCandidate(
                        "", "", "com.android.settings:id/switch_widget", "",
                        "", "", "android.widget.Switch", false,
                        new Rect(740, 105, 890, 155)),
                false, true, false, true, true, List.of(), List.of());

        SettingsSnapshot developer = snapshot("Opciones de desarrollador", List.of(
                row, container1, container2, container3, title, switchBackground, switchWidget));

        var target = classifier.classify(developer).target(TargetKey.WIRELESS_DEBUGGING);
        assertEquals(Confidence.HIGH, target.confidence());
        assertEquals(List.of(4), target.node().path());
        assertEquals("com.android.settings:id/preference_row", target.node().candidate().viewId());
        assertFalse(target.node().checkable());
        assertFalse(target.node().candidate().className().contains("Switch"));
    }

    @Test
    public void visibleWirelessLabelWithoutSafeAncestorIsDetectedForFailClosedNoScroll() {
        NodeSnapshot title = node(
                List.of(7, 0, 0, 0),
                new TargetCandidate(
                        "Depuración inalámbrica", "", "com.android.settings:id/title", "",
                        "", "", "android.widget.TextView", false,
                        new Rect(20, 100, 600, 160)),
                false,
                null,
                List.of());
        NodeSnapshot switchBackground = node(
                List.of(7, 1),
                new TargetCandidate(
                        "", "Depuración inalámbrica", "com.android.settings:id/switch_background",
                        "", "", "", "android.widget.Switch", true,
                        new Rect(700, 100, 900, 160)),
                false,
                null,
                List.of());

        SettingsSnapshot developer = snapshot(
                "Opciones de desarrollador", List.of(title, switchBackground));

        assertTrue(classifier.hasVisibleWirelessDebuggingLabel(developer));
        assertEquals(null, classifier.classify(developer).target(TargetKey.WIRELESS_DEBUGGING));
    }

    @Test
    public void snapshotCollectionsAreImmutableAndActionGateRejectsStaleToken() {
        ArrayList<Integer> path = new ArrayList<>(List.of(1));
        ArrayList<String> descendants = new ArrayList<>(List.of("Número de compilación"));
        NodeSnapshot node = new NodeSnapshot(
                path,
                candidate("", "parent", true),
                false, false, null, true, true, List.of(), descendants);
        path.add(2);
        descendants.add("otro");
        assertEquals(List.of(1), node.path());
        assertEquals(List.of("Número de compilación"), node.descendantTexts());

        SettingsSnapshot software = snapshot("Información de software", List.of(
                clickableRow("Número de compilación", "build")));
        var target = classifier.classify(software).target(TargetKey.BUILD_NUMBER);
        ScanGenerationGuard.Token token = new ScanGenerationGuard.Token(7, 1, "fixture");
        AutopilotActionGate gate = new AutopilotActionGate();
        assertTrue(gate.authorize(token, token, target, TargetKey.BUILD_NUMBER));
        assertFalse(gate.authorize(token,
                new ScanGenerationGuard.Token(8, 1, "fixture"), target, TargetKey.BUILD_NUMBER));
        assertFalse(gate.authorize(token, token, target, TargetKey.SOFTWARE_INFO));
        assertFalse(gate.authorize(token,
                new ScanGenerationGuard.Token(7, 2, "fixture"), target, TargetKey.BUILD_NUMBER));
        assertFalse(gate.authorize(token,
                new ScanGenerationGuard.Token(7, 1, "changed"), target, TargetKey.BUILD_NUMBER));
    }

    @Test
    public void networkConfirmationAndPairingContextRequireExactKnownScreens() {
        SettingsSnapshot confirmation = snapshot(
                "¿Permitir depuración inalámbrica en esta red?",
                List.of(clickableRow("Permitir", "allow")));
        assertEquals(Screen.NETWORK_CONFIRMATION, classifier.classify(confirmation).screen());
        assertEquals(Confidence.HIGH,
                classifier.classify(confirmation)
                        .target(TargetKey.NETWORK_CONFIRM_POSITIVE).confidence());

        SettingsSnapshot unknown = snapshot(
                "Ajustes",
                List.of(clickableRow("Permitir", "allow")));
        assertEquals(Screen.SETTINGS_HOME, classifier.classify(unknown).screen());
        assertEquals(null, classifier.classify(unknown).target(TargetKey.NETWORK_CONFIRM_POSITIVE));

        SettingsSnapshot randomSixDigits = snapshot(
                "Depuración inalámbrica",
                List.of(toggle(true), toggleState(true)),
                List.of(new VisibleText("123456", "", "Depuración inalámbrica")));
        assertEquals(Screen.WIRELESS_DEBUGGING, classifier.classify(randomSixDigits).screen());
        assertTrue(new ContextualPairingCodeDetector().detect(
                randomSixDigits, Screen.WIRELESS_DEBUGGING)
                instanceof ContextualPairingCodeDetector.Rejected);

        SettingsSnapshot titledPairing = snapshot(
                "Vincular dispositivo",
                List.of(clickableRow("Vincular dispositivo con código de vinculación", "pair")),
                List.of(
                        new VisibleText("Código de vinculación", "", "Vincular dispositivo"),
                        new VisibleText("123456", "Código de vinculación", "Vincular dispositivo")));
        assertEquals(Screen.PAIRING_DIALOG, classifier.classify(titledPairing).screen());
        assertEquals(new ContextualPairingCodeDetector.Unique("123456"),
                new ContextualPairingCodeDetector().detect(
                        titledPairing, Screen.PAIRING_DIALOG));
    }

    @Test
    public void wirelessTransitionWaitsForPostconditionAndNeverRepeatsToggle() {
        AutopilotTransitionGuard guard = new AutopilotTransitionGuard(2_500);
        guard.expect(
                EnumSet.of(Screen.WIRELESS_DEBUGGING, Screen.NETWORK_CONFIRMATION),
                true,
                1_000);
        assertEquals(AutopilotTransitionGuard.Result.WAIT,
                guard.evaluate(Screen.WIRELESS_DEBUGGING, false, 1_500));
        assertEquals(AutopilotTransitionGuard.Result.ACCEPT,
                guard.evaluate(Screen.WIRELESS_DEBUGGING, true, 2_000));

        guard.expect(EnumSet.of(Screen.SOFTWARE_INFO), null, 3_000);
        assertEquals(AutopilotTransitionGuard.Result.WAIT,
                guard.evaluate(Screen.ABOUT_PHONE, null, 4_000));
        assertEquals(AutopilotTransitionGuard.Result.REJECT,
                guard.evaluate(Screen.ABOUT_PHONE, null, 5_501));
    }

    @Test
    public void wirelessNavigationIsNotSuccessfulUntilScreenActuallyChanges() {
        AutopilotTransitionGuard guard = new AutopilotTransitionGuard(2_500);
        guard.expect(EnumSet.of(Screen.WIRELESS_DEBUGGING), null, 10_000);

        assertEquals(AutopilotTransitionGuard.Result.WAIT,
                guard.evaluate(Screen.DEVELOPER_OPTIONS, null, 10_500));
        assertEquals(AutopilotTransitionGuard.Result.WAIT,
                guard.evaluate(Screen.DEVELOPER_OPTIONS, null, 12_499));
        assertEquals(AutopilotTransitionGuard.Result.REJECT,
                guard.evaluate(Screen.DEVELOPER_OPTIONS, null, 12_500));

        guard.expect(EnumSet.of(Screen.WIRELESS_DEBUGGING), null, 20_000);
        assertEquals(AutopilotTransitionGuard.Result.ACCEPT,
                guard.evaluate(Screen.WIRELESS_DEBUGGING, null, 20_400));
    }

    private SettingsSnapshot snapshot(String title, List<NodeSnapshot> nodes) {
        return snapshot(title, nodes, List.of());
    }

    private SettingsSnapshot snapshot(
            String title, List<NodeSnapshot> nodes, List<VisibleText> visibleText) {
        return new SettingsSnapshot(1, "com.android.settings", title, "fixture", nodes, visibleText);
    }

    private NodeSnapshot clickableRow(String label, String pathKey) {
        return new NodeSnapshot(
                List.of(pathKey.hashCode()), candidate("", pathKey, true), false,
                false, null, true, true, List.of(), List.of(label));
    }

    private NodeSnapshot node(
            List<Integer> path,
            TargetCandidate candidate,
            boolean checkable,
            Boolean checked,
            List<String> descendants) {
        return new NodeSnapshot(
                path, candidate, false, checkable, checked, true, true, List.of(), descendants);
    }

    private NodeSnapshot toggle(boolean checked) {
        return new NodeSnapshot(
                List.of(9),
                new TargetCandidate(
                        "", "Depuración inalámbrica", "com.android.settings:id/switch_background",
                        "Depuración inalámbrica", "", "", "android.widget.Switch", true,
                        new Rect(0, 0, 100, 40)),
                false, false, null, true, true, List.of(), List.of());
    }

    private NodeSnapshot toggleState(boolean checked) {
        return new NodeSnapshot(
                List.of(9, 1),
                new TargetCandidate(
                        "", "", "com.android.settings:id/switch_widget", "Depuración inalámbrica",
                        "", "", "android.widget.Switch", false, new Rect(0, 0, 100, 40)),
                false, true, checked, true, true, List.of(), List.of());
    }

    private TargetCandidate candidate(String text, String id, boolean clickable) {
        return new TargetCandidate(
                text, "", "com.android.settings:id/" + id, "", "", "",
                "android.widget.LinearLayout", clickable, new Rect(0, 0, 100, 40));
    }
}
