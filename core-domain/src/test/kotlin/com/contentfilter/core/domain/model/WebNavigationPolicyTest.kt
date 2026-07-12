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
