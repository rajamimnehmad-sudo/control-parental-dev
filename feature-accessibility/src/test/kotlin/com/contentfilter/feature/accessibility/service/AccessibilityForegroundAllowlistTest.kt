package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityForegroundAllowlistTest {
    @Test
    fun `protected browser variants never enter generic app approval`() {
        assertTrue(AccessibilityForegroundAllowlist.contains("com.contentfilter.dagbrowser"))
        assertTrue(AccessibilityForegroundAllowlist.contains("com.contentfilter.dagbrowser.dev"))
        assertTrue(AccessibilityForegroundAllowlist.contains("com.contentfilter.dagbrowser.beta"))
    }

    @Test
    fun `project control apps and system launchers remain allowed`() {
        assertTrue(AccessibilityForegroundAllowlist.contains("com.contentfilter.user.dev"))
        assertTrue(AccessibilityForegroundAllowlist.contains("com.contentfilter.admin.dev"))
        assertTrue(AccessibilityForegroundAllowlist.contains("com.sec.android.app.launcher"))
    }

    @Test
    fun `ordinary browsers and unknown apps still require policy evaluation`() {
        assertFalse(AccessibilityForegroundAllowlist.contains("com.android.chrome"))
        assertFalse(AccessibilityForegroundAllowlist.contains("com.example.unknown"))
    }
}
