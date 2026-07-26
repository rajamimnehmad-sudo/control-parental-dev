package com.contentfilter.user.dag2

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal const val DagV2CalibrationPolicyVersion = "DAG_STRICT_MODESTY_V1"
internal const val DagV2CalibrationCollectorVersion = "dag-v2-calibration-collector-1"
internal const val DagV2CalibrationOutboxNamespace = "dag-v2-calibration-outbox-v1"
internal const val DagV2CalibrationRejectionNamespace = "dag-v2-calibration-rejections-v1"

enum class DagV2CalibrationDecision(
    val wireValue: String,
) {
    Show("show"),
    Hide("hide"),
    Unsure("unsure"),
    ;

    fun isTrainingExample(): Boolean = this != Unsure
}

data class DagV2CalibrationCandidate(
    val candidateId: String,
    val sessionId: String,
    val navigationToken: String,
    val resourceUrl: String,
    val documentOrigin: String,
    val resourceOrigin: String,
    val resourceKind: DagV2ResourceKind,
    val observedWidth: Int?,
    val observedHeight: Int?,
    val observedAt: Long,
    val attribution: DagV2RequestAttribution,
    val reviewable: Boolean,
) {
    val sanitizedResourceHost: String
        get() = resourceUrl.normalizedDagV2Host()

    val normalizedResourceIdentity: String
        get() =
            runCatching {
                val uri = URI(resourceUrl)
                URI(
                    uri.scheme?.lowercase(),
                    uri.authority?.lowercase(),
                    uri.path,
                    uri.query,
                    null,
                ).toString()
            }.getOrDefault(resourceUrl)

    companion object {
        fun from(
            request: DagV2ResourceRequest,
            session: DagV2DocumentSessionState,
            kind: DagV2ResourceKind,
            now: Long = System.currentTimeMillis(),
        ): DagV2CalibrationCandidate =
            DagV2CalibrationCandidate(
                candidateId = UUID.randomUUID().toString(),
                sessionId = session.sessionId,
                navigationToken = session.navigationToken,
                resourceUrl = request.url,
                documentOrigin = session.origin,
                resourceOrigin = request.url.dagV2Origin(),
                resourceKind = kind,
                observedWidth = null,
                observedHeight = null,
                observedAt = now,
                attribution = request.attribution,
                reviewable =
                    kind == DagV2ResourceKind.RasterImage &&
                        request.attribution == DagV2RequestAttribution.Current &&
                        request.url.isHttpsDagV2Url(),
            )
    }
}

data class DagV2CalibrationNormalizedImage(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
)

data class DagV2CalibrationFingerprintResult(
    val contentSha256: String,
    val perceptualHash: String,
)

data class DagV2CalibrationSubmission(
    val submissionId: String = UUID.randomUUID().toString(),
    val contentSha256: String,
    val perceptualHash: String,
    val jpegBytes: ByteArray?,
    val existingContentSha256: String? = null,
    val width: Int,
    val height: Int,
    val sourceKind: String,
    val sourceHost: String,
    val documentHost: String,
    val sourceUrlHash: String,
    val decision: DagV2CalibrationDecision,
    val policyVersion: String = DagV2CalibrationPolicyVersion,
    val collectorVersion: String = DagV2CalibrationCollectorVersion,
    val createdAt: String = Instant.now().toString(),
)

@Singleton
class DagV2CalibrationLocalDeduplicator
    @Inject
    constructor() {
        private val fingerprints = LinkedHashMap<String, String>()

        @Synchronized
        fun equivalent(fingerprint: DagV2CalibrationFingerprintResult): String? =
            fingerprints.entries
                .firstOrNull { (sha, perceptual) ->
                    sha == fingerprint.contentSha256 ||
                        DagV2CalibrationFingerprint.hammingDistance(
                            perceptual,
                            fingerprint.perceptualHash,
                        ) <= DagV2CalibrationFingerprint.NearDuplicateHammingDistance
                }?.key

        @Synchronized
        fun remember(fingerprint: DagV2CalibrationFingerprintResult) {
            fingerprints[fingerprint.contentSha256] = fingerprint.perceptualHash
            while (fingerprints.size > 500) fingerprints.remove(fingerprints.keys.first())
        }
    }

sealed interface DagV2CalibrationDeliveryResult {
    data class Accepted(
        val sampleId: String,
        val deduplicated: Boolean,
        val auditRecorded: Boolean,
    ) : DagV2CalibrationDeliveryResult

    data class TemporaryFailure(
        val reason: String,
    ) : DagV2CalibrationDeliveryResult

    data class PermanentFailure(
        val reason: String,
    ) : DagV2CalibrationDeliveryResult
}

sealed interface DagV2CalibrationEnqueueResult {
    data object Queued : DagV2CalibrationEnqueueResult

    data object Duplicate : DagV2CalibrationEnqueueResult

    data object Full : DagV2CalibrationEnqueueResult

    data object TooLarge : DagV2CalibrationEnqueueResult

    data object PersistenceFailure : DagV2CalibrationEnqueueResult
}

data class DagV2CalibrationReviewState(
    val enabled: Boolean = false,
    val candidates: List<DagV2CalibrationCandidate> = emptyList(),
    val reviewOpen: Boolean = false,
    val loadingCandidateId: String? = null,
    val previewCandidate: DagV2CalibrationCandidate? = null,
    val preview: DagV2CalibrationNormalizedImage? = null,
    val previewFingerprint: DagV2CalibrationFingerprintResult? = null,
    val sending: Boolean = false,
    val statusMessage: String? = null,
) {
    val candidateCount: Int
        get() = candidates.size
}

internal fun DagV2CalibrationReviewState.withEnqueueFailure(
    result: DagV2CalibrationEnqueueResult,
): DagV2CalibrationReviewState =
    copy(
        sending = false,
        statusMessage =
            when (result) {
                DagV2CalibrationEnqueueResult.Full ->
                    "Outbox lleno: la etiqueta no fue almacenada. Entregá pendientes y reintentá."
                DagV2CalibrationEnqueueResult.TooLarge ->
                    "La etiqueta no fue almacenada porque la evidencia excede el límite local."
                DagV2CalibrationEnqueueResult.PersistenceFailure ->
                    "La etiqueta no fue almacenada por un fallo local. Podés reintentar."
                DagV2CalibrationEnqueueResult.Queued,
                DagV2CalibrationEnqueueResult.Duplicate,
                -> error("enqueue_result_is_not_a_failure")
            },
    )

internal fun String.dagV2Sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

internal fun ByteArray.dagV2Sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
