package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagThemePreferenceTest {
    @Test
    fun `system theme follows device while explicit choices override it`() {
        assertTrue(dagUsesDarkTheme(DagThemePreference.System, systemDark = true))
        assertFalse(dagUsesDarkTheme(DagThemePreference.System, systemDark = false))
        assertFalse(dagUsesDarkTheme(DagThemePreference.Light, systemDark = true))
        assertTrue(dagUsesDarkTheme(DagThemePreference.Dark, systemDark = false))
    }
}
