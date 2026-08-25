package com.glosh.remote.spike.guide.autopilot;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;

import com.glosh.remote.spike.guide.accessibility.GuideTargetLocator;
import com.glosh.remote.spike.guide.accessibility.NodeSnapshot;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.accessibility.TargetCandidate;
import com.glosh.remote.spike.guide.accessibility.TargetMatcher;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.wizard.OemFamily;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SamsungOneUi8RecognitionTest {
    private final SamsungSettingsClassifier classifier = new SamsungSettingsClassifier();

    @Test
    public void aboutPhoneIsRecognizedFromVisibleOneUiMarkersWhenToolbarTitleIsMissed() {
        SettingsSnapshot snapshot = snapshot("S22 Ultra", List.of(
                label("Nombre del producto", 1),
                label("Nombre del modelo", 2),
                label("Número de serie", 3)));

        assertEquals(Screen.ABOUT_PHONE, classifier.classify(snapshot).screen());

        GuideTargetLocator locator = new GuideTargetLocator(new TargetMatcher());
        assertEquals(
                GuideStage.DEV_SOFTWARE_INFO,
                locator.locate(snapshot, OemFamily.SAMSUNG, GuideStage.DEV_ABOUT_PHONE, false).stage());
    }

    @Test
    public void softwareInfoIsRecognizedFromOneUi8MarkersAndAdvancesFallbackStage() {
        SettingsSnapshot snapshot = snapshot("", List.of(
                label("Versión de One UI", 1),
                label("Versión de Android", 2),
                label("Número de compilación", 3)));

        assertEquals(Screen.SOFTWARE_INFO, classifier.classify(snapshot).screen());

        GuideTargetLocator locator = new GuideTargetLocator(new TargetMatcher());
        assertEquals(
                GuideStage.DEV_BUILD_NUMBER,
                locator.locate(snapshot, OemFamily.SAMSUNG, GuideStage.DEV_SOFTWARE_INFO, false).stage());
    }

    @Test
    public void developerOptionsCanBeRecognizedWithoutCanonicalToolbarTitle() {
        SettingsSnapshot snapshot = snapshot("", List.of(
                label("Depuración USB", 1),
                label("Depuración inalámbrica", 2),
                label("Permanecer activo", 3)));

        assertEquals(Screen.DEVELOPER_OPTIONS, classifier.classify(snapshot).screen());
    }

    @Test
    public void wirelessDebuggingUsesPairingRowAsStrongScreenEvidence() {
        SettingsSnapshot snapshot = snapshot("", List.of(
                label("Vincular dispositivo con código de vinculación", 1)));

        assertEquals(Screen.WIRELESS_DEBUGGING, classifier.classify(snapshot).screen());
    }

    private SettingsSnapshot snapshot(String title, List<NodeSnapshot> nodes) {
        return new SettingsSnapshot(
                1,
                "com.android.settings",
                title,
                "fixture",
                nodes,
                List.of());
    }

    private NodeSnapshot label(String value, int index) {
        return new NodeSnapshot(
                List.of(index),
                new TargetCandidate(
                        value,
                        "",
                        "com.android.settings:id/title_" + index,
                        "",
                        "",
                        "",
                        "android.widget.TextView",
                        false,
                        new Rect(0, index * 20, 500, index * 20 + 18)),
                false,
                false,
                null,
                true,
                true,
                List.of(),
                List.of());
    }
}
