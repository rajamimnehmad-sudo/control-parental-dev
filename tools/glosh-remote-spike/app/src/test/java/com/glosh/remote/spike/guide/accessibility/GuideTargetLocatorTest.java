package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.wizard.OemFamily;

import org.junit.Test;

import java.util.List;

public class GuideTargetLocatorTest {
    private final GuideTargetLocator locator = new GuideTargetLocator(new TargetMatcher());

    @Test
    public void samsungVisibleBuildNumberNeverFallsIntoFalseRescue() {
        SettingsSnapshot snapshot = snapshot(
                "Información de software",
                node("Número de compilación", "Información de software"));
        GuideTargetLocator.LocatedTarget result = locator.locate(
                snapshot, OemFamily.SAMSUNG, GuideStage.DEV_BUILD_NUMBER, false);
        assertNotNull(result);
        assertEquals(GuideStage.DEV_BUILD_NUMBER, result.stage());
    }

    @Test
    public void wrongScreenDoesNotProduceTarget() {
        SettingsSnapshot snapshot = snapshot(
                "Privacidad", node("Número de compilación", "Privacidad"));
        assertNull(locator.locate(
                snapshot, OemFamily.SAMSUNG, GuideStage.DEV_BUILD_NUMBER, false));
    }

    @Test
    public void samsungStableNextScreenAdvancesAfterClickSnapshotWasInvalidated() {
        SettingsSnapshot snapshot = snapshot(
                "Información de software",
                node("Número de compilación", "Información de software"));
        GuideTargetLocator.LocatedTarget result = locator.locate(
                snapshot, OemFamily.SAMSUNG, GuideStage.DEV_SOFTWARE_INFO, false);
        assertNotNull(result);
        assertEquals(GuideStage.DEV_BUILD_NUMBER, result.stage());
    }

    @Test
    public void explicitRescueCanRealignKnownScreen() {
        SettingsSnapshot snapshot = snapshot(
                "Opciones de desarrollador",
                node("Depuración inalámbrica", "Opciones de desarrollador"));
        GuideTargetLocator.LocatedTarget result = locator.locate(
                snapshot, OemFamily.SAMSUNG, GuideStage.DEV_BUILD_NUMBER, true);
        assertNotNull(result);
        assertEquals(GuideStage.WIRELESS_DEBUGGING, result.stage());
    }

    private SettingsSnapshot snapshot(String title, NodeSnapshot node) {
        return new SettingsSnapshot(
                8, "com.android.settings", title, "fixture", List.of(node), List.of());
    }

    private NodeSnapshot node(String text, String screen) {
        return new NodeSnapshot(
                List.of(0),
                new TargetCandidate(
                        text, "", null, screen, "", "", "TextView", false,
                        new Rect(10, 10, 300, 100)),
                false);
    }
}
