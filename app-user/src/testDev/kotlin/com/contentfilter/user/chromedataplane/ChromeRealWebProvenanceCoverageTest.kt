package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeRealWebProvenanceCoverageTest {
    @Test
    fun `exact request and body identity is authoritative before render`() {
        val ledger = ledger()
        val token = ledger.request("c1-r1", "/image.png")
        ledger.recordInspected(token, 200, "image/png", response("body-a"))

        val classified = ledger.classify(listOf(claim(token, "body-a"))).single()

        assertEquals(ChromeVisibleMediaCoverage.AuthoritativePreRender, classified.coverage)
        assertEquals("c1-r1", classified.correlationId)
        val event = ledger.snapshot().events.single()
        assertTrue(event.token.observedSequence < requireNotNull(event.bodyCompleteSequence))
        assertTrue(requireNotNull(event.bodyCompleteSequence) < requireNotNull(event.inspectedSequence))
        assertTrue(requireNotNull(event.inspectedSequence) < requireNotNull(event.verdictReadySequence))
        assertTrue(requireNotNull(event.verdictReadySequence) < event.deliveredSequence)
    }

    @Test
    fun `same URL with different body never borrows authority`() {
        val ledger = ledger()
        val first = ledger.request("c1-r1", "/mutable.png")
        val second = ledger.request("c2-r1", "/mutable.png")
        ledger.recordInspected(first, 200, "image/png", response("body-a"))
        ledger.recordInspected(second, 200, "image/png", response("body-b"))

        val wrongBody = claim(first, "body-c")
        val exactSecond = claim(second, "body-b", correlationId = "c2-r1")
        val results = ledger.classify(listOf(wrongBody, exactSecond))

        assertEquals(ChromeVisibleMediaCoverage.AttributionUnknown, results[0].coverage)
        assertEquals(ChromeVisibleMediaCoverage.AuthoritativePreRender, results[1].coverage)
    }

    @Test
    fun `same body across request ids requires unique request identity`() {
        val ledger = ledger()
        val first = ledger.request("c1-r1", "/one.png")
        val second = ledger.request("c2-r1", "/one.png")
        ledger.recordInspected(first, 200, "image/png", response("same"))
        ledger.recordInspected(second, 200, "image/png", response("same"))

        val ambiguous = ledger.classify(listOf(claim(first, "same"))).single()
        val exact = ledger.classify(listOf(claim(first, "same", correlationId = "c1-r1"))).single()

        assertEquals(ChromeVisibleMediaCoverage.AttributionUnknown, ambiguous.coverage)
        assertEquals(ChromeVisibleMediaCoverage.AuthoritativePreRender, exact.coverage)
    }

    @Test
    fun `one delivered resource cannot authorize two visible instances without explicit proof`() {
        val ledger = ledger()
        val token = ledger.request("c1-r1", "/shared.png")
        ledger.recordInspected(token, 200, "image/png", response("shared"))

        val first = claim(token, "shared").copy(instanceId = "first")
        val second = claim(token, "shared").copy(instanceId = "second")
        val results = ledger.classify(listOf(first, second))

        assertEquals(ChromeVisibleMediaCoverage.AuthoritativePreRender, results[0].coverage)
        assertEquals(ChromeVisibleMediaCoverage.AttributionUnknown, results[1].coverage)
        assertEquals("visible_instance_reuse_not_proven", results[1].reason)
    }

    @Test
    fun `redirect lineage binds only an observed target request`() {
        val ledger = ledger()
        val redirect = ledger.request("c1-r1", "/redirect")
        ledger.recordRedirect(redirect, 302, "/final.png")

        val final = ledger.request("c2-r1", "/final.png")
        val unrelated = ledger.request("c3-r1", "/other.png")

        assertEquals("c1-r1", final.redirectFromCorrelationId)
        assertNull(unrelated.redirectFromCorrelationId)
    }

    @Test
    fun `verdict cache hit is recorded without changing exact identity`() {
        val ledger = ledger()
        val token = ledger.request("c1-r1", "/cached.png")
        ledger.recordInspected(token, 200, "image/png", response("cached", cacheHit = true))

        val event = ledger.snapshot().events.single()

        assertTrue(event.verdictCacheHit)
        assertEquals("cache", event.verdictSource)
        assertEquals(sha256("cached".toByteArray()), event.bodyDigest)
    }

    @Test
    fun `unknown visible identity never promotes to authoritative`() {
        val ledger = ledger()
        val token = ledger.request("c1-r1", "/image.png")
        ledger.recordInspected(token, 200, "image/png", response("body-a"))

        val result =
            ledger.classify(
                listOf(
                    ChromeVisibleMediaClaim(
                        instanceId = "visible",
                        stateSequence = token.stateSequence,
                        navigationSequence = token.navigationSequence,
                    ),
                ),
            ).single()

        assertEquals(ChromeVisibleMediaCoverage.AttributionUnknown, result.coverage)
    }

    @Test
    fun `positive renderer local mechanism is definite non interceptable`() {
        val ledger = ledger()
        val result =
            ledger.classify(
                listOf(
                    ChromeVisibleMediaClaim(
                        instanceId = "canvas",
                        stateSequence = 1,
                        navigationSequence = 1,
                        nonInterceptableReason = ChromeNonInterceptableReason.Canvas,
                    ),
                ),
            ).single()

        assertEquals(ChromeVisibleMediaCoverage.DefiniteNonInterceptable, result.coverage)
        assertEquals("Canvas", result.reason)
    }

    @Test
    fun `telemetry is bounded and redacts URL referrer cookies and authorization`() {
        val lines = mutableListOf<String>()
        val ledger = ChromeRealWebProvenanceLedger(maximumEvents = 2, emit = lines::add)
        ledger.beginSession("session-private")
        ledger.markState("fravega listing", newNavigation = true)
        repeat(3) { index ->
            val token =
                ledger.beginRequest(
                    host = "private.example",
                    request =
                        ChromePhotosProxyRequest(
                            method = "GET",
                            target = "/secret-$index.png?account=alice",
                            headers =
                                listOf(
                                    ChromeHttpHeader("Referer", "https://shop.example/user/alice"),
                                    ChromeHttpHeader("Cookie", "session=secret"),
                                    ChromeHttpHeader("Authorization", "Bearer token"),
                                    ChromeHttpHeader("Sec-Fetch-Dest", "image"),
                                ),
                        ),
                    correlationId = "c$index-r1",
                )
            ledger.recordInspected(token, 200, "image/png", response("body-$index"))
        }

        val snapshot = ledger.snapshot()
        val output = lines.joinToString("\n")

        assertEquals(2, snapshot.events.size)
        assertEquals(1, snapshot.droppedEvents)
        assertFalse(output.contains("private.example"))
        assertFalse(output.contains("secret-"))
        assertFalse(output.contains("alice"))
        assertFalse(output.contains("Bearer"))
        assertFalse(output.contains("session=secret"))
    }

    @Test
    fun `new navigation does not mix previous provenance`() {
        val ledger = ledger()
        val token = ledger.request("c1-r1", "/image.png")
        ledger.recordInspected(token, 200, "image/png", response("body-a"))
        ledger.markState("new-page", newNavigation = true)

        val stale = claim(token, "body-a").copy(stateSequence = 2, navigationSequence = 2)
        val result = ledger.classify(listOf(stale)).single()

        assertEquals(ChromeVisibleMediaCoverage.AttributionUnknown, result.coverage)
    }

    @Test
    fun `session reset and stop clear all retained state`() {
        val ledger = ledger()
        val old = ledger.request("c1-r1", "/image.png")
        ledger.recordInspected(old, 200, "image/png", response("body-a"))

        ledger.beginSession("session-two")
        assertTrue(ledger.snapshot().events.isEmpty())
        ledger.close()
        assertTrue(ledger.snapshot().events.isEmpty())
        assertEquals("", ledger.snapshot().sessionId)
    }

    private fun ledger(): ChromeRealWebProvenanceLedger =
        ChromeRealWebProvenanceLedger().also { ledger ->
            ledger.beginSession("session-one")
            ledger.markState("state-one", newNavigation = true)
        }

    private fun ChromeRealWebProvenanceLedger.request(
        correlationId: String,
        target: String,
    ): ChromeCoverageRequestToken =
        beginRequest(
            host = "images.example",
            request =
                ChromePhotosProxyRequest(
                    method = "GET",
                    target = target,
                    headers =
                        listOf(
                            ChromeHttpHeader("Referer", "https://shop.example/page"),
                            ChromeHttpHeader("Sec-Fetch-Dest", "image"),
                        ),
                ),
            correlationId = correlationId,
        )

    private fun response(
        body: String,
        cacheHit: Boolean = false,
    ): ChromePhotosSanitizedResponse =
        ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers = listOf(ChromeHttpHeader("Content-Type", "image/png")),
            bytes = body.toByteArray(),
            decision = ChromePhotosResourceDecision.Safe,
            cacheHit = cacheHit,
            contentHash = sha256(body.toByteArray()),
            inputBytes = body.length,
            observedBodyDigest = sha256(body.toByteArray()),
            decisionResult =
                ChromePhotoDecisionResult(
                    decision = ChromePhotoDecision.Safe,
                    reason = "model_allow",
                    source = if (cacheHit) ChromePhotoDecisionSource.Cache else ChromePhotoDecisionSource.Engine,
                ),
        )

    private fun claim(
        token: ChromeCoverageRequestToken,
        body: String,
        correlationId: String? = null,
    ): ChromeVisibleMediaClaim =
        ChromeVisibleMediaClaim(
            instanceId = "visible",
            stateSequence = token.stateSequence,
            navigationSequence = token.navigationSequence,
            requestUrlHash = token.requestUrlHash,
            bodyDigest = sha256(body.toByteArray()),
            correlationId = correlationId,
        )
}
