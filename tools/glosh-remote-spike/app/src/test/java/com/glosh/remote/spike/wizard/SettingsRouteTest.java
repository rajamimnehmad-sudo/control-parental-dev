package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;

import android.provider.Settings;

import java.util.Set;

import org.junit.Test;

public class SettingsRouteTest {
    @Test
    public void accessibilityOpensTheExactServiceDestinationFirst() {
        assertEquals(SettingsRoute.ACCESSIBILITY_DETAILS,
                SettingsRoute.accessibilityActions().get(0));
        assertEquals(
                SettingsRoute.ACCESSIBILITY_DETAILS,
                SettingsRoute.firstResolvable(
                        SettingsRoute.accessibilityActions(),
                        Set.of(SettingsRoute.ACCESSIBILITY_DETAILS)::contains));
        assertEquals(
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                SettingsRoute.firstResolvable(
                        SettingsRoute.accessibilityActions(),
                        Set.of(Settings.ACTION_ACCESSIBILITY_SETTINGS)::contains));
    }

    @Test
    public void wirelessDebuggingUsesBestAvailableFallback() {
        assertEquals(SettingsRoute.WIRELESS_DEBUGGING,
                SettingsRoute.wirelessDebuggingActions().get(0));
        assertEquals(
                SettingsRoute.WIRELESS_DEBUGGING,
                SettingsRoute.firstResolvable(
                        SettingsRoute.wirelessDebuggingActions(),
                        Set.of(SettingsRoute.WIRELESS_DEBUGGING)::contains));
        assertEquals(
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                SettingsRoute.firstResolvable(
                        SettingsRoute.wirelessDebuggingActions(),
                        Set.of(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)::contains));
        assertEquals(
                Settings.ACTION_SETTINGS,
                SettingsRoute.firstResolvable(
                        SettingsRoute.wirelessDebuggingActions(),
                        action -> false));
    }

    @Test
    public void aboutPhoneFallsBackToGeneralSettings() {
        assertEquals(
                Settings.ACTION_SETTINGS,
                SettingsRoute.firstResolvable(
                        SettingsRoute.aboutPhoneActions(),
                        Set.of(Settings.ACTION_SETTINGS)::contains));
    }
}
