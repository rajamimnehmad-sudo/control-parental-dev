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
)

/** DEV-only delivery gate used to falsify raw network-visual escape routes. */
internal class ChromeNetworkVisualDeliveryGate(
    private val mode: ChromeNetworkVisualDeliveryMode = ChromeNetworkVisualDeliveryMode.Selective,
    private val auditPlaceholderBytes: ByteArray = ByteArray(0),
) {
    private val candidates = AtomicLong()
    private val replaced = AtomicLong()
    private val rawDelivered = AtomicLong()

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

    fun snapshot(): ChromeNetworkVisualDeliverySnapshot =
        ChromeNetworkVisualDeliverySnapshot(
            candidates = candidates.get(),
            replaced = replaced.get(),
            rawDelivered = rawDelivered.get(),
        )

    companion object {
        private const val AuditPlaceholderContentType = "image/png"
    }
}
