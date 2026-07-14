package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebNavigationPolicyTest {
    @Test
    fun `legacy Google preference is read until canonical preference exists`() {
        val legacy = rule(WebNavigationPolicy.LegacyGoogleResultsAllowedTarget, enabled = true)

        assertTrue(listOf(legacy).externalSearchResultsAllowed())
        assertFalse(
            listOf(
                legacy,
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, enabled = false),
            ).externalSearchResultsAllowed(),
        )
    }

    @Test
    fun `external navigation requires a search engine source`() {
        assertTrue(WebNavigationPolicy.isExternalSearchNavigation("google.com", "example.com"))
        assertTrue(WebNavigationPolicy.isExternalSearchNavigation("bing.com", "example.com"))
        assertTrue(WebNavigationPolicy.isExternalSearchNavigation("search.yahoo.com", "example.com"))
        assertTrue(WebNavigationPolicy.isExternalSearchNavigation("duckduckgo.com", "example.com"))
        assertFalse(WebNavigationPolicy.isExternalSearchNavigation("example.com", "another.example"))
        assertFalse(WebNavigationPolicy.isExternalSearchNavigation("google.com", "bing.com"))
    }

    @Test
    fun `missing result preference defaults to full navigation and ignores removed image fields`() {
        val removedImageRule = rule("__web_images_blocked__", enabled = true).copy(action = RuleAction.Block)

        assertTrue(listOf(removedImageRule).externalSearchResultsAllowed())
        assertFalse(listOf(removedImageRule).onlySearchResultsEnabled())
        assertTrue(listOf(removedImageRule).safeSearchEnabled())
    }

    @Test
    fun `Solo resultados conversion is explicit and reversible`() {
        assertTrue(WebProtectionSemantics.onlyResultsEnabled(externalSearchResultsAllowed = false))
        assertFalse(WebProtectionSemantics.onlyResultsEnabled(externalSearchResultsAllowed = true))
        assertFalse(WebProtectionSemantics.externalSearchResultsAllowed(onlyResultsEnabled = true))
        assertTrue(WebProtectionSemantics.externalSearchResultsAllowed(onlyResultsEnabled = false))
    }

    @Test
    fun `Solo resultados requires an explicit Web preference rule`() {
        assertFalse(emptyList<PolicyRule>().onlySearchResultsEnabled())
        assertTrue(
            listOf(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, enabled = false),
            ).onlySearchResultsEnabled(),
        )
    }

    @Test
    fun `DAG is closed by default and opens only with its enabled allow rule`() {
        assertFalse(emptyList<PolicyRule>().dagEnabled())
        assertFalse(listOf(rule(WebNavigationPolicy.DagEnabledTarget, enabled = false)).dagEnabled())
        assertFalse(
            listOf(
                rule(WebNavigationPolicy.DagEnabledTarget, enabled = true).copy(action = RuleAction.Block),
            ).dagEnabled(),
        )
        assertTrue(listOf(rule(WebNavigationPolicy.DagEnabledTarget, enabled = true)).dagEnabled())
    }

    private fun rule(
        target: String,
        enabled: Boolean,
    ): PolicyRule =
        PolicyRule(
            id = target,
            level = PolicyLevel.Device,
            scope = RuleScope.Domain,
            target = target,
            action = RuleAction.Allow,
            priority = 0,
            enabled = enabled,
        )
}
