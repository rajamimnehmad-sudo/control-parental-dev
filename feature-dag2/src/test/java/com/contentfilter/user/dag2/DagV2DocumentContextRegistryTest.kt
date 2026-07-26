package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagV2DocumentContextRegistryTest {
    @Test
    fun `webview-bound request keeps its original generation after a new document starts`() {
        val sessions = DagV2DocumentSession()
        val registry = DagV2DocumentContextRegistry()
        val first = sessions.start("https://example.com/a").requestContext
        registry.register(first)
        sessions.cancelActive()
        registry.cancel(first)
        val second = sessions.start("https://example.com/b").requestContext
        registry.register(second)

        val attributed =
            registry.resolveBound(
                first,
                DagV2ResourceEvidence(
                    url = "https://cdn.example.com/late.js",
                    headers = emptyMap(),
                    isForMainFrame = false,
                    source = DagV2ResourceSource.WebView,
                ),
            )

        assertEquals(DagV2RequestAttribution.Stale, attributed.attribution)
        assertEquals(first, attributed.context)
        assertTrue(registry.isCurrent(second))
    }

    private val sessions = DagV2DocumentSession()
    private val contexts = DagV2DocumentContextRegistry()
    private val gate = DagV2DocumentCallbackGate(contexts, sessions)

    @Test
    fun `late callback from document A cannot change document B`() {
        val first = start("https://example.com/a")
        val firstAnalysis = sessions.beginFullAnalysis(first.sessionId, first.navigationToken)
        assertNotNull(firstAnalysis)
        sessions.cancelActive()
        contexts.cancel(first)

        val second = start("https://example.com/b")
        val before = sessions.snapshot()
        assertFalse(gate.accepts(first))

        val staleCompletion =
            if (gate.accepts(first)) {
                sessions.completeFullAnalysis(first.sessionId, first.navigationToken)
            } else {
                null
            }

        assertNull(staleCompletion)
        assertEquals(before, sessions.snapshot())
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(0, sessions.snapshot()?.fullPageAnalysisCount)
        assertFalse(sessions.snapshot()?.fullAnalysisCompleted ?: true)
    }

    @Test
    fun `bridge rejects stale generation and non main frame`() {
        val first = start("https://example.com/a")
        sessions.cancelActive()
        contexts.cancel(first)
        val second = start("https://example.com/b")

        assertNull(
            gate.authorizeBridgeMessage(
                first.sessionId,
                first.navigationToken,
                first.documentOrigin,
                isMainFrame = true,
            ),
        )
        assertNull(
            gate.authorizeBridgeMessage(
                second.sessionId,
                second.navigationToken,
                second.documentOrigin,
                isMainFrame = false,
            ),
        )
        assertNull(
            gate.authorizeBridgeMessage(
                second.sessionId,
                second.navigationToken,
                "https://iframe.example",
                isMainFrame = true,
            ),
        )
        assertNotNull(
            gate.authorizeBridgeMessage(
                second.sessionId,
                second.navigationToken,
                second.documentOrigin,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun `spa alias keeps the same generation and does not repeat full analysis`() {
        val context = start("https://example.com/products")
        val initial = sessions.beginFullAnalysis(context.sessionId, context.navigationToken)
        assertNotNull(initial)
        assertTrue(contexts.registerSpaLocation(context, "https://example.com/products?filter=1"))
        sessions.recordInternalInteraction(DagV2InternalInteraction.PushState)

        val attributed =
            contexts.resolve(
                DagV2ResourceEvidence(
                    url = "https://example.com/api/products",
                    headers = mapOf("Referer" to "https://example.com/products?filter=1"),
                    isForMainFrame = false,
                    source = DagV2ResourceSource.WebView,
                ),
            )

        assertEquals(DagV2RequestAttribution.Current, attributed.attribution)
        assertEquals(context.navigationToken, attributed.context?.navigationToken)
        assertEquals(1, sessions.snapshot()?.fullPageAnalysisCount)
        assertNull(sessions.beginFullAnalysis(context.sessionId, context.navigationToken))
    }

    @Test
    fun `explicit navigation generation disambiguates a same url reload`() {
        val first = start("https://example.com/products")
        sessions.cancelActive()
        contexts.cancel(first)
        val second = start("https://example.com/products")

        val attributed =
            contexts.resolve(
                DagV2ResourceEvidence(
                    url = second.documentUrl,
                    headers = mapOf(DagV2NavigationTokenHeader to second.navigationToken),
                    isForMainFrame = true,
                    source = DagV2ResourceSource.WebView,
                ),
            )

        assertEquals(DagV2RequestAttribution.Current, attributed.attribution)
        assertEquals(second.navigationToken, attributed.context?.navigationToken)
    }

    private fun start(url: String): DagV2DocumentRequestContext =
        sessions.start(url).requestContext.also(contexts::register)
}
