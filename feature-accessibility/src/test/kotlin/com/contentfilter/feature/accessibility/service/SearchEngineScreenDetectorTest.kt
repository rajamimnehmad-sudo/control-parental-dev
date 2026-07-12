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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `restricted mode leaves new external attempts to VPN without closing Chrome`() {
        val detector = SearchEngineScreenDetector()
        detector.diagnose(Chrome, snapshot(), currentHost = "google.com", elapsedRealtimeMillis = 100L)

        val diagnosis =
            detector.diagnose(
                packageName = Chrome,
                snapshot = snapshot(),
                currentHost = "example.com",
                elapsedRealtimeMillis = 101L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("external-domain-blocked-by-vpn", diagnosis.reason)
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
    fun `manual address navigation is blocked by VPN without Accessibility action`() {
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
        assertEquals("external-domain-blocked-by-vpn", diagnosis.reason)
    }

    @Test
    fun `SafeSearch does not change restricted navigation decision`() {
        val detector = SearchEngineScreenDetector()
        val safeSearch = snapshot(rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow))
        detector.diagnose(Chrome, safeSearch, currentHost = "search.yahoo.com", elapsedRealtimeMillis = 100L)

        val diagnosis = detector.diagnose(Chrome, safeSearch, currentHost = "example.com", elapsedRealtimeMillis = 101L)

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("yahoo", diagnosis.searchEngineId)
    }

    @Test
    fun `image filtering uses overlays without Back or Home`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot =
                    snapshot(
                        rule(WebNavigationPolicy.ImagesBlockedTarget, RuleAction.Block),
                        rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
                    ),
                currentHost = "google.com",
                mediaSearchView = true,
                elapsedRealtimeMillis = 100L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("external-results-allowed", diagnosis.reason)
        assertTrue(diagnosis.imagesBlocked)
        assertTrue(diagnosis.mediaSearchView)
    }

    @Test
    fun `image search remains available when image filtering is off`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot = snapshot(rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow)),
                currentHost = "bing.com",
                mediaSearchView = true,
                elapsedRealtimeMillis = 100L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertFalse(diagnosis.imagesBlocked)
    }

    @Test
    fun `private Chrome session receives the same image and web decisions`() {
        val policy = snapshot(rule(WebNavigationPolicy.ImagesBlockedTarget, RuleAction.Block))
        val normal =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot = policy,
                currentHost = "duckduckgo.com",
                mediaSearchView = true,
                elapsedRealtimeMillis = 100L,
            )
        val private =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot = policy,
                currentHost = "duckduckgo.com",
                mediaSearchView = true,
                elapsedRealtimeMillis = 101L,
            )

        assertEquals(normal.action, private.action)
        assertEquals(normal.reason, private.reason)
    }

    @Test
    fun `recent search signal does not trigger repeated Back for a new blocked request`() {
        val diagnosis =
            SearchEngineScreenDetector().diagnose(
                packageName = Chrome,
                snapshot = snapshot(),
                currentHost = "example.com",
                recentSearchEngineId = "google",
                elapsedRealtimeMillis = 100L,
            )

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("external-domain-blocked-by-vpn", diagnosis.reason)
        assertEquals("google", diagnosis.searchEngineId)
    }

    @Test
    fun `activating Solo resultados on an existing external tab goes back exactly once`() {
        val detector = SearchEngineScreenDetector()
        val released =
            snapshot(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
                version = 20L,
            )
        val restricted =
            snapshot(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow).copy(enabled = false),
                version = 21L,
            )
        detector.diagnose(Chrome, released, currentHost = "google.com", elapsedRealtimeMillis = 100L)
        detector.diagnose(Chrome, released, currentHost = "example.com", elapsedRealtimeMillis = 101L)

        val first = detector.diagnose(Chrome, restricted, currentHost = "example.com", elapsedRealtimeMillis = 102L)
        val repeated = detector.diagnose(Chrome, restricted, currentHost = "example.com", elapsedRealtimeMillis = 103L)

        assertEquals(SearchNavigationAction.GoBack, first.action)
        assertEquals(SearchNavigationAction.Allow, repeated.action)
        assertEquals("external-domain-blocked-by-vpn", repeated.reason)
    }

    @Test
    fun `activating Solo resultados without a search origin opens default search once`() {
        val detector = SearchEngineScreenDetector()
        val released =
            snapshot(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
                version = 30L,
            )
        val restricted =
            snapshot(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow).copy(enabled = false),
                version = 31L,
            )
        detector.diagnose(Chrome, released, currentHost = "example.com", elapsedRealtimeMillis = 100L)

        val diagnosis =
            detector.diagnose(Chrome, restricted, currentHost = "example.com", elapsedRealtimeMillis = 101L)

        assertEquals(SearchNavigationAction.OpenDefaultSearch, diagnosis.action)
    }

    @Test
    fun `direct favorites history redirects restored tabs and incognito require no Accessibility action`() {
        val entryPoints = listOf("direct", "favorite", "history", "redirect", "restored", "incognito")

        entryPoints.forEachIndexed { index, _ ->
            val detector = SearchEngineScreenDetector()
            detector.diagnose(Chrome, snapshot(), currentHost = "google.com", elapsedRealtimeMillis = 100L)
            val diagnosis =
                detector.diagnose(
                    Chrome,
                    snapshot(),
                    currentHost = "external-$index.example",
                    elapsedRealtimeMillis = 101L,
                )

            assertEquals(SearchNavigationAction.Allow, diagnosis.action)
            assertEquals("external-domain-blocked-by-vpn", diagnosis.reason)
        }
    }

    @Test
    fun `expired search session does not block unrelated navigation`() {
        val detector = SearchEngineScreenDetector(searchSessionWindowMillis = 100L)
        detector.diagnose(Chrome, snapshot(), currentHost = "duckduckgo.com", elapsedRealtimeMillis = 1L)

        val diagnosis = detector.diagnose(Chrome, snapshot(), currentHost = "example.com", elapsedRealtimeMillis = 102L)

        assertEquals(SearchNavigationAction.Allow, diagnosis.action)
        assertEquals("external-domain-blocked-by-vpn", diagnosis.reason)
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
    fun `Accessibility honors every cumulative Web protection combination`() {
        repeat(16) { bits ->
            val webBlocked = bits and 1 != 0
            val externalResultsAllowed = bits and 2 != 0
            val imagesBlocked = bits and 4 != 0
            val policy = snapshot(*webRules(bits).toTypedArray())
            val detector = SearchEngineScreenDetector()

            val diagnosis =
                when {
                    webBlocked ->
                        detector.diagnose(
                            packageName = Chrome,
                            snapshot = policy,
                            currentHost = "google.com",
                            mediaSearchView = true,
                            elapsedRealtimeMillis = 100L,
                        )
                    else -> {
                        detector.diagnose(Chrome, policy, currentHost = "google.com", elapsedRealtimeMillis = 100L)
                        detector.diagnose(Chrome, policy, currentHost = "example.com", elapsedRealtimeMillis = 101L)
                    }
                }

            val expected =
                when {
                    webBlocked -> SearchNavigationAction.GoHome
                    else -> SearchNavigationAction.Allow
                }
            assertEquals(expected, diagnosis.action)
            assertEquals(webBlocked, diagnosis.webNavigationBlocked)
            assertEquals(externalResultsAllowed, diagnosis.externalSearchResultsAllowed)
            assertEquals(imagesBlocked, diagnosis.imagesBlocked)
        }
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

    @Test
    fun `address parser retains only host and media classification`() {
        val google =
            SearchEngineScreenDetector.addressObservationFromAddressBarText(
                "https://www.google.com/search?q=private&tbm=isch",
            )
        val bing =
            SearchEngineScreenDetector.addressObservationFromAddressBarText(
                "https://www.bing.com/videos/search?q=private",
            )
        val yahoo =
            SearchEngineScreenDetector.addressObservationFromAddressBarText(
                "https://images.search.yahoo.com/search/images?p=private",
            )
        val duckDuckGo =
            SearchEngineScreenDetector.addressObservationFromAddressBarText(
                "https://duckduckgo.com/?q=private&ia=images",
            )

        assertEquals("google.com", google?.host)
        assertTrue(google?.mediaSearchView == true)
        assertTrue(bing?.mediaSearchView == true)
        assertTrue(yahoo?.mediaSearchView == true)
        assertTrue(duckDuckGo?.mediaSearchView == true)
        assertFalse(google.toString().contains("private"))
    }

    private fun snapshot(
        vararg rules: PolicyRule,
        version: Long = 10L,
    ): PolicySnapshot =
        PolicySnapshot(
            id = "test",
            version = version,
            rules =
                rules.toList().let { provided ->
                    if (provided.any { it.target == WebNavigationPolicy.ExternalSearchResultsAllowedTarget }) {
                        provided
                    } else {
                        provided +
                            rule(
                                WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                                RuleAction.Allow,
                            ).copy(enabled = false)
                    }
                },
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

    private fun webRules(bits: Int): List<PolicyRule> =
        listOf(
            rule(WebNavigationPolicy.RuleTarget, RuleAction.Block).copy(enabled = bits and 1 != 0),
            rule(
                WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                RuleAction.Allow,
            ).copy(enabled = bits and 2 != 0),
            rule(WebNavigationPolicy.ImagesBlockedTarget, RuleAction.Block).copy(enabled = bits and 4 != 0),
            rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow).copy(enabled = bits and 8 != 0),
        )

    private companion object {
        const val Chrome = "com.android.chrome"
    }
}
