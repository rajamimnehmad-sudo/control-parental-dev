package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SamsungGuideStepTest {
    @Test
    public void guideHasSevenExplicitSamsungSteps() {
        assertEquals(7, SamsungGuideStep.values().length);
        assertEquals(1, SamsungGuideStep.ABOUT_PHONE.number());
        assertEquals(7, SamsungGuideStep.ENTER_CODE.number());
        assertEquals(SamsungGuideStep.TOTAL_STEPS, SamsungGuideStep.ENTER_CODE.number());
    }

    @Test
    public void buildNumberVisualShowsSevenTapEffect() {
        assertTrue(SamsungGuideStep.BUILD_NUMBER.visual().showSevenTaps());
        assertTrue(SamsungGuideStep.BUILD_NUMBER.title().contains("7 veces"));
    }

    @Test
    public void settingsNavigationStartsGeneralThenUsesSafeDestinations() {
        assertEquals(
                SamsungGuideStep.SettingsTarget.NONE,
                SamsungGuideStep.ABOUT_PHONE.settingsTarget());
        assertEquals(
                SamsungGuideStep.SettingsTarget.ABOUT_PHONE,
                SamsungGuideStep.SOFTWARE_INFO.settingsTarget());
        assertEquals(
                SamsungGuideStep.SettingsTarget.DEVELOPER_OPTIONS,
                SamsungGuideStep.DEVELOPER_OPTIONS.settingsTarget());
        assertEquals(
                SamsungGuideStep.SettingsTarget.WIRELESS_DEBUGGING,
                SamsungGuideStep.WIRELESS_DEBUGGING.settingsTarget());
        assertEquals(
                SamsungGuideStep.SettingsTarget.WIRELESS_DEBUGGING,
                SamsungGuideStep.PAIR_DEVICE.settingsTarget());
    }

    @Test
    public void backAndNextAreBounded() {
        assertFalse(SamsungGuideStep.ABOUT_PHONE.canGoBack());
        assertEquals(SamsungGuideStep.ABOUT_PHONE, SamsungGuideStep.ABOUT_PHONE.previous());
        assertEquals(SamsungGuideStep.SOFTWARE_INFO, SamsungGuideStep.ABOUT_PHONE.next());
        assertEquals(SamsungGuideStep.PAIR_DEVICE, SamsungGuideStep.ENTER_CODE.previous());
        assertEquals(SamsungGuideStep.ENTER_CODE, SamsungGuideStep.ENTER_CODE.next());
        assertFalse(SamsungGuideStep.ENTER_CODE.canAdvanceLocally());
    }

    @Test
    public void criticalMilestonesHaveExplicitConfirmLabels() {
        assertEquals("MODO DESARROLLADOR ACTIVADO", SamsungGuideStep.BUILD_NUMBER.confirmLabel());
        assertEquals("YA LA ACTIVÉ", SamsungGuideStep.WIRELESS_DEBUGGING.confirmLabel());
        assertEquals("YA VEO LOS 6 NÚMEROS", SamsungGuideStep.PAIR_DEVICE.confirmLabel());
    }
}
