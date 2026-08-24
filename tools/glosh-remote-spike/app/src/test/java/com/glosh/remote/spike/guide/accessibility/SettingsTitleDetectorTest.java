package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsTitleDetectorTest {
    @Test
    public void samsungCollapsingToolbarMakesDescendantTextATitle() {
        assertTrue(SettingsTitleDetector.isTitleContainer(
                "com.android.settings:id/collapsing_toolbar"));
        assertTrue(SettingsTitleDetector.isTitleContainer(
                "com.android.settings:id/action_bar"));
        assertFalse(SettingsTitleDetector.isTitleContainer("android:id/title"));
        assertFalse(SettingsTitleDetector.isTitleContainer(null));
    }

    @Test
    public void samsungNavigateUpDescriptionCannotReplaceScreenTitle() {
        assertEquals("", SettingsTitleDetector.explicitTitle(null, "Navegar hacia arriba"));
        assertEquals(
                "Acerca del teléfono",
                SettingsTitleDetector.explicitTitle("Acerca del teléfono", ""));
    }
}
