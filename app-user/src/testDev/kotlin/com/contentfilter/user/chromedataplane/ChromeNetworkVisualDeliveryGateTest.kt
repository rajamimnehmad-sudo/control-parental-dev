package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChromeNetworkVisualDeliveryGateTest {
    @Test
    fun `replace all has a distinct delivery outcome and exact audit bytes`() {
        val audit = "fixed-audit-placeholder".toByteArray()
        val gate =
            ChromeNetworkVisualDeliveryGate(
                mode = ChromeNetworkVisualDeliveryMode.ReplaceAll,
                auditPlaceholderBytes = audit,
            )

        val response = gate.replaceAllResponse(upstream())

        requireNotNull(response)
        assertEquals(ChromePhotosResourceDecision.AuditReplaced, response.decision)
        assertNull(response.decisionResult)
        assertContentEquals(audit, response.bytes)
        assertEquals("no-store", response.headers.firstValue("Cache-Control"))
        gate.recordCandidateDelivery(response.bytes)
        assertEquals(ChromeNetworkVisualDeliverySnapshot(1, 1, 0), gate.snapshot())
    }

    @Test
    fun `wire-side delivery metric detects any non-audit candidate body`() {
        val gate =
            ChromeNetworkVisualDeliveryGate(
                mode = ChromeNetworkVisualDeliveryMode.ReplaceAll,
                auditPlaceholderBytes = "audit".toByteArray(),
            )

        gate.replaceAllResponse(upstream())
        gate.recordCandidateDelivery("raw-sentinel".toByteArray())

        assertEquals(ChromeNetworkVisualDeliverySnapshot(1, 1, 1), gate.snapshot())
    }

    @Test
    fun `selective mode never claims an audit replacement`() {
        val gate = ChromeNetworkVisualDeliveryGate()

        assertNull(gate.replaceAllResponse(upstream()))
        gate.recordCandidateDelivery("safe-original".toByteArray())

        assertEquals(ChromeNetworkVisualDeliverySnapshot(1, 0, 0), gate.snapshot())
    }

    private fun upstream() =
        ChromePhotosUpstreamResponse(
            host = "glosh-photos.test",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(ChromeHttpHeader("Content-Type", "image/png")),
            body = "raw-sentinel".byteInputStream(),
            bodyLength = 12,
            protocol = "fixture",
        )
}
