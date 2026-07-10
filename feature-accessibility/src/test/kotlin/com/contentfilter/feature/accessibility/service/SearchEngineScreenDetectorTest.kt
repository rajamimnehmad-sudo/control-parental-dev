package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchEngineScreenDetectorTest {
    private val detector = SearchEngineScreenDetector()

    @Test
    fun `does not leave Chrome search screen when web navigation is open`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search",
            ),
        )
    }

    @Test
    fun `does not leave Chrome results screen when web navigation is open`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Resultados de búsqueda",
            ),
        )
    }

    @Test
    fun `does not leave Chrome after recent blocked Google DNS host when web navigation is open`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Nueva pestaña",
                recentDnsBlockHost = "google.com",
            ),
        )
    }

    @Test
    fun `does not leave Chrome when search engines are allowed`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Allow)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `leaves browser immediately when web navigation is blocked`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block)),
                visibleText = "Nueva pestaña",
            )

        assertTrue(diagnosis.shouldLeave)
        assertEquals("web-navigation-blocked", diagnosis.reason)
    }

    @Test
    fun `does not leave normal Chrome page when search engines are blocked`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Wikipedia Enciclopedia libre Historia Artículo",
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
    fun `does not leave Google app search screen when web navigation is open`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.google.android.googlequicksearchbox",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            )

        assertFalse(diagnosis.shouldLeave)
        assertEquals("searchApp", diagnosis.packageCategory)
        assertEquals("web-navigation-open", diagnosis.reason)
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
        assertEquals("web-navigation-open", diagnosis.reason)
        assertEquals(1, diagnosis.searchBlockRules)
    }

    @Test
    fun `diagnoses Chrome recent DNS search block as open when web navigation is open`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Nueva pestaña",
                recentDnsBlockHost = "google.com",
            )

        assertFalse(diagnosis.shouldLeave)
        assertEquals("browser", diagnosis.packageCategory)
        assertEquals("web-navigation-open", diagnosis.reason)
        assertEquals(null, diagnosis.recentDnsBlockHost)
    }


    @Test
    fun `diagnoses browser search screen as open when web navigation is open`() {
        val diagnosis =
            detector.diagnose(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            )

        assertFalse(diagnosis.shouldLeave)
        assertEquals("web-navigation-open", diagnosis.reason)
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
