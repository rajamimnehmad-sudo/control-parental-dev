package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchEngineScreenDetectorTest {
    @Test
    fun `global web block sends browser home regardless of result preference`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot =
                    snapshot(
                        rule(WebNavigationPolicy.RuleTarget, RuleAction.Block),
                        rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
                    ),
                currentHost = "google.com",
                elapsedRealtimeMillis = 1L,
            )

        assertEquals(SearchNavigationAction.GoHome, diagnosis.action)
        assertEquals("web-navigation-blocked", diagnosis.reason)
    }

    @Test
    fun `restricted mode keeps every supported search engine open`() {
        val detector = SearchEngineScreenDetector()

        mapOf(
            "google.com" to "google",
            "bing.com" to "bing",
            "search.yahoo.com" to "yahoo",
            "duckduckgo.com" to "duckduckgo",
        ).forEach { (host, engineId) ->
            val diagnosis =
                detector.diagnose(
                    packageName = Chrome,
                    snapshot = snapshot(),
                    currentHost = host,
                    elapsedRealtimeMillis = 10L,
                )

            assertEquals(SearchNavigationAction.Allow, diagnosis.action)
            assertEquals("search-results-visible", diagnosis.reason)
            assertEquals(engineId, diagnosis.searchEngineId)
        }
    }

    @Test
    fun `restricted mode goes back when an external result opens`() {
        val detector = SearchEngineScreenDetector()
        detector.diagnose(Chrome, snapshot(), currentHost = "google.com", elapsedRealtimeMillis = 100L)

        val diagnosis =
            detector.diagnose(
                packageName = Chrome,
                snapshot = snapshot(),
                currentHost = "example.com",
                elapsedRealtimeMillis = 101L,
            )

        assertEquals(SearchNavigationAction.GoBack, diagnosis.action)
        assertEquals("external-result-restricted", diagnosis.reason)
        assertEquals("google", diagnosis.searchEngineId)
    }

    @Test
    fun `released mode allows an external result`() {
        val detector = SearchEngineScreenDetector()
        val released = snapshot(rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow))
        detector.diagnose(Chrome, released, currentHost = "duckduckgo.com", elapsedRealtimeMillis = 100L)

        val diagnosis = detector.diagnose(Chrome, released, currentHost = "example.com", elapsedRealtimeMillis = 101L)

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("external-results-allowed", diagnosis.reason)
    }

    @Test
    fun `address bar navigation is not mistaken for a result click`() {
        val detector = SearchEngineScreenDetector()
        detector.diagnose(Chrome, snapshot(), currentHost = "bing.com", elapsedRealtimeMillis = 100L)

        val diagnosis =
            detector.diagnose(
                packageName = Chrome,
                snapshot = snapshot(),
                currentHost = "example.com",
                addressBarFocused = true,
                elapsedRealtimeMillis = 101L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("address-bar-navigation", diagnosis.reason)
    }

    @Test
    fun `SafeSearch does not change restricted navigation decision`() {
        val detector = SearchEngineScreenDetector()
        val safeSearch = snapshot(rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow))
        detector.diagnose(Chrome, safeSearch, currentHost = "search.yahoo.com", elapsedRealtimeMillis = 100L)

        val diagnosis = detector.diagnose(Chrome, safeSearch, currentHost = "example.com", elapsedRealtimeMillis = 101L)

        assertEquals(SearchNavigationAction.GoBack, diagnosis.action)
        assertEquals("yahoo", diagnosis.searchEngineId)
    }

    @Test
    fun `recent search engine signal handles transition from search app to browser`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot = snapshot(),
                currentHost = "example.com",
                recentSearchEngineId = "google",
                elapsedRealtimeMillis = 100L,
            )

        assertEquals(SearchNavigationAction.GoBack, diagnosis.action)
        assertEquals("google", diagnosis.searchEngineId)
    }

    @Test
    fun `expired search session does not block unrelated navigation`() {
        val detector = SearchEngineScreenDetector(searchSessionWindowMillis = 100L)
        detector.diagnose(Chrome, snapshot(), currentHost = "duckduckgo.com", elapsedRealtimeMillis = 1L)

        val diagnosis = detector.diagnose(Chrome, snapshot(), currentHost = "example.com", elapsedRealtimeMillis = 102L)

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("no-search-transition", diagnosis.reason)
    }

    @Test
    fun `non browser app is never treated as a result transition`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = "com.example.app",
                snapshot = snapshot(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block)),
                currentHost = "google.com",
                elapsedRealtimeMillis = 1L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("non-browser", diagnosis.reason)
    }

    @Test
    fun `address parser extracts host without retaining path or query`() {
        assertEquals(
            "google.com",
            SearchEngineScreenDetector.hostFromAddressBarText("https://www.google.com/search?q=private"),
        )
        assertEquals("bing.com", SearchEngineScreenDetector.hostFromAddressBarText("www.bing.com/search"))
        assertNull(SearchEngineScreenDetector.hostFromAddressBarText("Buscar o escribir dirección"))
    }

    private fun snapshot(vararg rules: PolicyRule): PolicySnapshot =
        PolicySnapshot(
            id = "test",
            version = 10L,
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

    private companion object {
        const val Chrome = "com.android.chrome"
    }
}
