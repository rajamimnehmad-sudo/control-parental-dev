package com.contentfilter.user.chromedataplane

import java.util.concurrent.atomic.AtomicLong

internal enum class ChromeNetworkVisualDeliveryMode {
    Selective,
    ReplaceAll,
}

internal data class ChromeNetworkVisualDeliverySnapshot(
    val candidates: Long = 0,
    val replaced: Long = 0,
    val rawDelivered: Long = 0,
    val safeRawDelivered: Long = 0,
    val blockedReplaced: Long = 0,
    val unknownReplaced: Long = 0,
    val unsupportedReplaced: Long = 0,
    val rawBlockedDelivered: Long = 0,
    val rawUnknownDelivered: Long = 0,
    val cacheHit: Long = 0,
    val inference: Long = 0,
)

/** DEV-only delivery gate used to falsify raw network-visual escape routes. */
internal class ChromeNetworkVisualDeliveryGate(
    private val mode: ChromeNetworkVisualDeliveryMode = ChromeNetworkVisualDeliveryMode.Selective,
    private val auditPlaceholderBytes: ByteArray = ByteArray(0),
    private val replacementPlaceholderBytes: ByteArray = ByteArray(0),
) {
    private val candidates = AtomicLong()
    private val replaced = AtomicLong()
    private val rawDelivered = AtomicLong()
    private val safeRawDelivered = AtomicLong()
    private val blockedReplaced = AtomicLong()
    private val unknownReplaced = AtomicLong()
    private val unsupportedReplaced = AtomicLong()
    private val rawBlockedDelivered = AtomicLong()
    private val rawUnknownDelivered = AtomicLong()
    private val cacheHit = AtomicLong()
    private val inference = AtomicLong()

    init {
        require(mode != ChromeNetworkVisualDeliveryMode.ReplaceAll || auditPlaceholderBytes.isNotEmpty())
    }

    fun replaceAllResponse(upstream: ChromePhotosUpstreamResponse): ChromePhotosSanitizedResponse? {
        candidates.incrementAndGet()
        if (mode != ChromeNetworkVisualDeliveryMode.ReplaceAll) return null
        replaced.incrementAndGet()
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers = ChromeHttpHeaderPolicy.transformedImageHeaders(upstream.headers, AuditPlaceholderContentType),
            bytes = auditPlaceholderBytes,
            decision = ChromePhotosResourceDecision.AuditReplaced,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
            decisionResult = null,
        )
    }

    fun recordCandidateDelivery(bytes: ByteArray) {
        if (
            mode == ChromeNetworkVisualDeliveryMode.ReplaceAll &&
            bytes.isNotEmpty() &&
            !bytes.contentEquals(auditPlaceholderBytes)
        ) {
            rawDelivered.incrementAndGet()
        }
    }

    /**
     * Final pre-wire barrier. Metrics detect regressions after a successful write, while this
     * predicate prevents any malformed BLOCK/UNKNOWN/AUDIT response from reaching Chrome at all.
     */
    fun isCandidateDeliveryAuthorized(response: ChromePhotosSanitizedResponse): Boolean =
        when (response.decision) {
            ChromePhotosResourceDecision.Safe ->
                response.decisionResult?.decision == ChromePhotoDecision.Safe &&
                    response.contentHash?.let { digest ->
                        digest.matches(Sha256Pattern) && sha256(response.bytes) == digest
                    } == true
            ChromePhotosResourceDecision.Block,
            ChromePhotosResourceDecision.Unknown,
            -> response.bytes.isExpectedReplacement()
            ChromePhotosResourceDecision.AuditReplaced ->
                mode == ChromeNetworkVisualDeliveryMode.ReplaceAll &&
                    auditPlaceholderBytes.isNotEmpty() &&
                    response.bytes.contentEquals(auditPlaceholderBytes)
            ChromePhotosResourceDecision.Passthrough -> response.bytes.isEmpty()
        }

    fun recordCandidateDelivery(response: ChromePhotosSanitizedResponse) {
        when (response.decision) {
            ChromePhotosResourceDecision.Safe -> safeRawDelivered.incrementAndGet()
            ChromePhotosResourceDecision.Block -> {
                if (response.bytes.isExpectedReplacement()) {
                    blockedReplaced.incrementAndGet()
                } else {
                    rawBlockedDelivered.incrementAndGet()
                }
            }
            ChromePhotosResourceDecision.Unknown -> {
                if (response.bytes.isExpectedReplacement()) {
                    if (response.decisionResult?.reason.isUnsupportedVisualReason()) {
                        unsupportedReplaced.incrementAndGet()
                    } else {
                        unknownReplaced.incrementAndGet()
                    }
                } else {
                    rawUnknownDelivered.incrementAndGet()
                }
            }
            ChromePhotosResourceDecision.AuditReplaced -> recordCandidateDelivery(response.bytes)
            ChromePhotosResourceDecision.Passthrough -> Unit
        }
        if (response.cacheHit) cacheHit.incrementAndGet()
        if (response.decisionResult?.source == ChromePhotoDecisionSource.Engine) inference.incrementAndGet()
    }

    fun snapshot(): ChromeNetworkVisualDeliverySnapshot =
        ChromeNetworkVisualDeliverySnapshot(
            candidates = candidates.get(),
            replaced = replaced.get(),
            rawDelivered = rawDelivered.get(),
            safeRawDelivered = safeRawDelivered.get(),
            blockedReplaced = blockedReplaced.get(),
            unknownReplaced = unknownReplaced.get(),
            unsupportedReplaced = unsupportedReplaced.get(),
            rawBlockedDelivered = rawBlockedDelivered.get(),
            rawUnknownDelivered = rawUnknownDelivered.get(),
            cacheHit = cacheHit.get(),
            inference = inference.get(),
        )

    private fun ByteArray.isExpectedReplacement(): Boolean =
        replacementPlaceholderBytes.isNotEmpty() && contentEquals(replacementPlaceholderBytes)

    private fun String?.isUnsupportedVisualReason(): Boolean =
        this != null && (startsWith(UnsupportedReasonPrefix) || this in UnsupportedVisualReasons)

    companion object {
        private const val AuditPlaceholderContentType = "image/png"
        private const val UnsupportedReasonPrefix = "unsupported_"
        private val Sha256Pattern = Regex("[0-9a-f]{64}")
        private val UnsupportedVisualReasons =
            setOf(
                "animated_image",
                "encoded_image_unsupported",
                "image_byte_limit",
                "image_format_unknown",
                "image_not_modified_without_current_authority",
                "partial_image_entity",
                "unsafe_dimensions",
            )
    }
}
