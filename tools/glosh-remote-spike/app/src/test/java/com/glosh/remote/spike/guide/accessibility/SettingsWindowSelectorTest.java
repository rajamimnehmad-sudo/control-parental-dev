package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class SettingsWindowSelectorTest {
    private static final String SETTINGS = "com.android.settings";
    private final SettingsWindowSelector selector = new SettingsWindowSelector();

    @Test
    public void overlayAndImeCanNeverBeAuthority() {
        SettingsWindowSelector.Selection selection = selector.select(List.of(
                window(1, SettingsWindowSelector.WindowType.ACCESSIBILITY_OVERLAY, SETTINGS, true),
                window(2, SettingsWindowSelector.WindowType.INPUT_METHOD, SETTINGS, true)), Set.of(SETTINGS));
        assertFalse(selection.selected());
    }

    @Test
    public void nonSettingsApplicationCanNeverBeAuthority() {
        SettingsWindowSelector.Selection selection = selector.select(List.of(
                window(1, SettingsWindowSelector.WindowType.APPLICATION, "com.android.chrome", true)),
                Set.of(SETTINGS));
        assertFalse(selection.selected());
    }

    @Test
    public void uniqueSettingsApplicationIsSelected() {
        SettingsWindowSelector.Selection selection = selector.select(List.of(
                window(1, SettingsWindowSelector.WindowType.ACCESSIBILITY_OVERLAY, "com.glosh.remote.spike", true),
                window(2, SettingsWindowSelector.WindowType.APPLICATION, SETTINGS, true)), Set.of(SETTINGS));
        assertTrue(selection.selected());
        assertTrue(selection.window().id() == 2);
    }

    @Test
    public void twoEquallyAuthoritativeSettingsWindowsFailClosed() {
        SettingsWindowSelector.Selection selection = selector.select(List.of(
                window(1, SettingsWindowSelector.WindowType.APPLICATION, SETTINGS, true),
                window(2, SettingsWindowSelector.WindowType.APPLICATION, SETTINGS, true)), Set.of(SETTINGS));
        assertFalse(selection.selected());
        assertTrue(selection.ambiguous());
    }

    @Test
    public void twoSettingsWindowsFailClosedEvenWhenOnlyOneIsActive() {
        SettingsWindowSelector.Selection selection = selector.select(List.of(
                window(1, SettingsWindowSelector.WindowType.APPLICATION, SETTINGS, false),
                window(2, SettingsWindowSelector.WindowType.APPLICATION, SETTINGS, true)), Set.of(SETTINGS));
        assertFalse(selection.selected());
        assertTrue(selection.ambiguous());
    }

    private SettingsWindowSelector.WindowCandidate window(
            int id, SettingsWindowSelector.WindowType type, String packageName, boolean active) {
        return new SettingsWindowSelector.WindowCandidate(id, type, packageName, active, active);
    }
}
