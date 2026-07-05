package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchEngineScreenDetectorTest {
    private val detector = SearchEngineScreenDetector()

    @Test
    fun `leaves browser search screen when search engines are blocked`() {
        assertTrue(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `does not leave browser when search engines are allowed`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Allow)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `does not leave non browser app`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.example.app",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `leaves Google app search screen when search engines are blocked`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.google.android.googlequicksearchbox",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            )

        assertTrue(diagnosis.shouldLeave)
        assertEquals("searchApp", diagnosis.packageCategory)
        assertEquals("blocked-search-screen", diagnosis.reason)
    }

    @Test
    fun `diagnoses blocked browser screen without visible search signal`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Nueva pestaña",
            )

        assertFalse(diagnosis.shouldLeave)
        assertEquals("no-search-signal", diagnosis.reason)
        assertEquals(1, diagnosis.searchBlockRules)
    }

    @Test
    fun `leaves Chrome without visible signal after recent DNS search block`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Nueva pestaña",
                recentDnsBlockHost = "google.com",
            )

        assertTrue(diagnosis.shouldLeave)
        assertEquals("browser", diagnosis.packageCategory)
        assertEquals("dueToRecentDnsBlock", diagnosis.reason)
        assertEquals("google.com", diagnosis.recentDnsBlockHost)
    }


    @Test
    fun `diagnoses blocked search screen`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            )

        assertTrue(diagnosis.shouldLeave)
        assertEquals("blocked-search-screen", diagnosis.reason)
        assertEquals(1, diagnosis.searchBlockRules)
    }

    private fun snapshot(vararg rules: PolicyRule): PolicySnapshot =
        PolicySnapshot(
            id = "test",
            version = 1L,
            rules = rules.toList(),
        )

    private fun rule(
        target: String,
        action: RuleAction,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target-$action",
            level = PolicyLevel.Device,
            scope = RuleScope.Domain,
            target = target,
            action = action,
            priority = 0,
            enabled = true,
        )
}
