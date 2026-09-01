package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaimResult
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeMediaShieldRendererMetricsTest {
    @AfterTest
    fun clearRegistry() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun `claimed document records one bounded renderer snapshot and rejects replay`() {
        val token = "abcdefghijklmnopqrstuv"
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("session", 20)
        val issued = assertNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue("session", 20, token, true))
        val identity =
            ChromeMediaShieldSelfReadyIdentity(
                "session",
                20,
                issued.navigationSequence,
                issued.documentSequence,
                1,
                true,
            )
        assertTrue(ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(token, identity) is ChromeMediaShieldReadyClaimResult.Claimed)
        val values = List(ChromeMediaShieldRendererMetricsSnapshot.FieldCount) { (it + 1).toLong() }
        val body =
            (
                "v1|RENDERER_METRICS|$token|session|20|${identity.navigationSequence}|" +
                    "${identity.documentSequence}|1|T|${values.joinToString(",")}"
            ).toByteArray()
        val report = assertNotNull(ChromeMediaShieldRendererMetrics.parse(body))
        val metrics = ChromeMediaShieldRendererMetrics()

        assertTrue(metrics.record(report))
        assertFalse(metrics.record(report))
        assertEquals(1, metrics.snapshot().reports)
        assertEquals(1, metrics.snapshot().rejected)
        assertEquals(1, metrics.snapshot()[0])
        assertEquals(38, metrics.snapshot()[37])
    }

    @Test
    fun `unclaimed stale malformed and wrong field count fail closed`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("session", 20)
        val token = "abcdefghijklmnopqrstuv"
        val issued = assertNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue("session", 20, token, true))
        val identity = ChromeMediaShieldSelfReadyIdentity("session", 20, 1, issued.documentSequence, 1, true)
        val metrics = ChromeMediaShieldRendererMetrics()
        val values = List(ChromeMediaShieldRendererMetricsSnapshot.FieldCount) { 0L }

        assertFalse(metrics.record(ChromeMediaShieldRendererMetricsReport(token, identity, values)))
        assertEquals(null, ChromeMediaShieldRendererMetrics.parse("v1|RENDERER_METRICS|bad".toByteArray()))
        assertFalse(
            metrics.record(
                ChromeMediaShieldRendererMetricsReport(
                    token,
                    identity.copy(protectionSessionId = "other"),
                    values,
                ),
            ),
        )
        assertFalse(metrics.record(ChromeMediaShieldRendererMetricsReport(token, identity, values.dropLast(1))))
    }
}
