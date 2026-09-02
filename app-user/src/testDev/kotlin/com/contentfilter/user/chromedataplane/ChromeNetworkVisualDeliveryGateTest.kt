package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `selective wire metrics distinguish exact safe and fail-close replacements`() {
        val placeholder = "placeholder".toByteArray()
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = placeholder)
        gate.replaceAllResponse(upstream())
        gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Safe, "safe".toByteArray(), "model_allow"))
        gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Block, placeholder, "model_filter"))
        gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Unknown, placeholder, "engine_unavailable"))
        gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Unknown, placeholder, "unsupported_svg"))

        val snapshot = gate.snapshot()
        assertEquals(
            4L,
            snapshot.safeRawDelivered + snapshot.blockedReplaced + snapshot.unknownReplaced + snapshot.unsupportedReplaced,
        )
        assertEquals(1L, snapshot.safeRawDelivered)
        assertEquals(1L, snapshot.blockedReplaced)
        assertEquals(1L, snapshot.unknownReplaced)
        assertEquals(1L, snapshot.unsupportedReplaced)
        assertEquals(0L, snapshot.rawBlockedDelivered)
        assertEquals(0L, snapshot.rawUnknownDelivered)
    }

    @Test
    fun `wire metrics expose any BLOCK or UNKNOWN body that is not the placeholder`() {
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = "placeholder".toByteArray())

        gate.recordCandidateDelivery(
            response(ChromePhotosResourceDecision.Block, "raw-block".toByteArray(), "model_filter"),
        )
        gate.recordCandidateDelivery(
            response(ChromePhotosResourceDecision.Unknown, "raw-unknown".toByteArray(), "decode_error"),
        )

        assertEquals(1L, gate.snapshot().rawBlockedDelivered)
        assertEquals(1L, gate.snapshot().rawUnknownDelivered)
    }

    @Test
    fun `pre-wire authority admits exact SAFE and replacements but rejects corrupt delivery`() {
        val safe = "safe-original".toByteArray()
        val placeholder = "placeholder".toByteArray()
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = placeholder)

        assertTrue(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Safe, safe, "model_allow").copy(contentHash = sha256(safe)),
            ),
        )
        assertTrue(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Block, placeholder, "model_filter"),
            ),
        )
        assertTrue(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Unknown, placeholder, "decode_failed"),
            ),
        )
        assertFalse(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Safe, "mutated".toByteArray(), "model_allow")
                    .copy(contentHash = sha256(safe)),
            ),
        )
        assertFalse(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Block, "raw-block".toByteArray(), "model_filter"),
            ),
        )
        assertFalse(
            gate.isCandidateDeliveryAuthorized(
                response(ChromePhotosResourceDecision.Unknown, "raw-unknown".toByteArray(), "decode_failed"),
            ),
        )
    }

    @Test
    fun `pre-wire authority requires SAFE transport and analyzer decision to agree`() {
        val bytes = "safe-original".toByteArray()
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = "placeholder".toByteArray())
        val safe =
            response(ChromePhotosResourceDecision.Safe, bytes, "model_allow")
                .copy(contentHash = sha256(bytes))

        assertTrue(gate.isCandidateDeliveryAuthorized(safe))
        assertFalse(gate.isCandidateDeliveryAuthorized(safe.copy(decisionResult = null)))
        assertFalse(
            gate.isCandidateDeliveryAuthorized(
                safe.copy(
                    decisionResult =
                        safe.decisionResult?.copy(
                            decision = ChromePhotoDecision.Block,
                            reason = "model_filter",
                        ),
                ),
            ),
        )
        assertFalse(
            gate.isCandidateDeliveryAuthorized(
                safe.copy(
                    decisionResult =
                        safe.decisionResult?.copy(
                            decision = ChromePhotoDecision.Unknown,
                            reason = "engine_unavailable",
                        ),
                ),
            ),
        )
    }

    @Test
    fun `pre-wire audit authority accepts only its fixed placeholder`() {
        val audit = "audit".toByteArray()
        val gate =
            ChromeNetworkVisualDeliveryGate(
                mode = ChromeNetworkVisualDeliveryMode.ReplaceAll,
                auditPlaceholderBytes = audit,
            )

        val exact = requireNotNull(gate.replaceAllResponse(upstream()))
        assertTrue(gate.isCandidateDeliveryAuthorized(exact))
        assertFalse(gate.isCandidateDeliveryAuthorized(exact.copy(bytes = "raw".toByteArray())))
    }

    @Test
    fun `unsupported metric covers every fail closed transport and format class`() {
        val placeholder = "placeholder".toByteArray()
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = placeholder)
        val reasons =
            listOf(
                "unsupported_svg",
                "animated_image",
                "encoded_image_unsupported",
                "image_byte_limit",
                "image_format_unknown",
                "image_not_modified_without_current_authority",
                "partial_image_entity",
                "unsafe_dimensions",
            )

        reasons.forEach { reason ->
            gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Unknown, placeholder, reason))
        }

        val snapshot = gate.snapshot()
        assertEquals(reasons.size.toLong(), snapshot.unsupportedReplaced)
        assertEquals(0L, snapshot.unknownReplaced)
        assertEquals(0L, snapshot.rawUnknownDelivered)
    }

    @Test
    fun `operational and integrity failures remain unknown rather than unsupported`() {
        val placeholder = "placeholder".toByteArray()
        val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = placeholder)
        val reasons =
            listOf(
                "analyzer_unavailable",
                "image_body_admission_interrupted",
                "image_format_changed_after_peek",
                "decode_failed",
            )

        reasons.forEach { reason ->
            gate.recordCandidateDelivery(response(ChromePhotosResourceDecision.Unknown, placeholder, reason))
        }

        val snapshot = gate.snapshot()
        assertEquals(reasons.size.toLong(), snapshot.unknownReplaced)
        assertEquals(0L, snapshot.unsupportedReplaced)
        assertEquals(0L, snapshot.rawUnknownDelivered)
    }

    private fun response(
        decision: ChromePhotosResourceDecision,
        bytes: ByteArray,
        reason: String,
    ) = ChromePhotosSanitizedResponse(
        statusCode = 200,
        statusText = "OK",
        headers = listOf(ChromeHttpHeader("Content-Type", "image/png")),
        bytes = bytes,
        decision = decision,
        cacheHit = false,
        contentHash = "a".repeat(64),
        inputBytes = bytes.size,
        decisionResult =
            ChromePhotoDecisionResult(
                decision =
                    when (decision) {
                        ChromePhotosResourceDecision.Safe -> ChromePhotoDecision.Safe
                        ChromePhotosResourceDecision.Block -> ChromePhotoDecision.Block
                        else -> ChromePhotoDecision.Unknown
                    },
                reason = reason,
                source = ChromePhotoDecisionSource.Engine,
            ),
    )

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
