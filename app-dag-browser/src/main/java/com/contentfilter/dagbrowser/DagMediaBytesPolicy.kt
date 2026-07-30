package com.contentfilter.dagbrowser

import android.graphics.BitmapFactory
import java.net.URI
import java.util.Base64

internal data class DagMediaBytesPayload(
    val candidateId: String,
    val sourceUrl: String,
    val declaredByteLength: Int,
    val bytesBase64: String,
)

internal data class DagImageBounds(
    val width: Int,
    val height: Int,
    val mimeType: String,
)

internal fun interface DagImageBoundsReader {
    fun read(bytes: ByteArray): DagImageBounds?
}

internal object AndroidImageBoundsReader : DagImageBoundsReader {
    override fun read(bytes: ByteArray): DagImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType.isNullOrBlank()) {
            return null
        }
        return DagImageBounds(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType,
        )
    }
}

/**
 * Validates and preprocesses a bounded image response before the local classifier sees pixels.
 *
 * Passing the envelope and decode boundary never means that media is safe. Only a valid result
 * from the bundled model can produce allow; every error and unsupported input remains block.
 */
internal object DagMediaBytesPolicy {
    private val candidateIdPattern = Regex("^[A-Za-z0-9_-]{1,80}$")

    fun decide(
        payload: DagMediaBytesPayload,
        boundsReader: DagImageBoundsReader = AndroidImageBoundsReader,
        preprocessor: DagImagePreprocessor = AndroidDagImagePreprocessor,
        analyzer: DagImageAnalyzer = UnavailableDagImageAnalyzer,
    ): DagMediaDecision {
        if (!validEnvelope(payload)) return blocked(payload, InvalidPayloadReason)
        return inspectImage(payload, boundsReader, preprocessor, analyzer)
    }

    private fun validEnvelope(payload: DagMediaBytesPayload): Boolean {
        if (!candidateIdPattern.matches(payload.candidateId)) return false
        if (!isAllowedUrl(payload.sourceUrl)) return false
        if (payload.declaredByteLength !in 1..MaxCaptureBytes) return false
        return payload.bytesBase64.length in 1..MaxBase64Length
    }

    private fun inspectImage(
        payload: DagMediaBytesPayload,
        boundsReader: DagImageBoundsReader,
        preprocessor: DagImagePreprocessor,
        analyzer: DagImageAnalyzer,
    ): DagMediaDecision {
        val bytes =
            runCatching { Base64.getDecoder().decode(payload.bytesBase64) }
                .getOrElse { return blocked(payload, InvalidPayloadReason) }
        if (bytes.size != payload.declaredByteLength || bytes.size > MaxCaptureBytes) {
            return blocked(payload, InvalidPayloadReason)
        }
        if (DagSafeUiVectorPolicy.isSafe(bytes)) {
            return DagMediaDecision(
                candidateId = payload.candidateId,
                action = DagMediaAction.Allow,
                reason = SafeUiVectorReason,
            )
        }
        val bounds = boundsReader.read(bytes) ?: return blocked(payload, UnsupportedImageReason)
        if (bounds.mimeType !in DagImageDecodeContract.SupportedMimeTypes) {
            return blocked(payload, UnsupportedImageReason)
        }
        if (!DagImageDecodeContract.hasSafeDimensions(bounds.width, bounds.height)) {
            return blocked(payload, UnsafeDimensionsReason)
        }
        return when (
            val result =
                runCatching { preprocessor.prepare(bytes) }
                    .getOrElse {
                        DagImagePreprocessResult.Rejected(
                            AndroidDagImagePreprocessor.DecodeFailedReason,
                        )
                    }
        ) {
            is DagImagePreprocessResult.Ready -> {
                val preparedImages = listOf(result.image) + result.regionalImages
                val valid = preparedImages.all(DagImageDecodeContract::isValid)
                try {
                    if (!valid) {
                        blocked(payload, AndroidDagImagePreprocessor.DecodeFailedReason)
                    } else {
                        decidePreparedImages(payload, preparedImages, analyzer)
                    }
                } catch (_: Exception) {
                    blocked(payload, DagOnDeviceImageAnalyzer.ModelExecutionFailedReason)
                } finally {
                    preparedImages.forEach { it.rgb888.fill(0) }
                }
            }
            is DagImagePreprocessResult.Rejected -> blocked(payload, result.reason)
        }
    }

    private fun decidePreparedImages(
        payload: DagMediaBytesPayload,
        preparedImages: List<DagPreparedImage>,
        analyzer: DagImageAnalyzer,
    ): DagMediaDecision {
        val fullProbability =
            when (val analysis = analyzer.analyze(preparedImages.first())) {
                is DagImageAnalysisResult.Classified ->
                    analysis.filterProbability.takeIf(::isValidProbability)
                        ?: return blocked(
                            payload,
                            DagOnDeviceImageAnalyzer.InvalidModelOutputReason,
                        )
                is DagImageAnalysisResult.Unavailable ->
                    return blocked(payload, analysis.reason)
            }
        if (fullProbability >= DagOnDeviceImageAnalyzer.FilterThreshold) {
            return blocked(
                payload,
                DagOnDeviceImageAnalyzer.ModelFilterReason,
                fullProbability,
            )
        }

        var maximumProbability = fullProbability
        for (regionalImage in preparedImages.drop(1)) {
            val regionalProbability =
                when (val analysis = analyzer.analyze(regionalImage)) {
                    is DagImageAnalysisResult.Classified ->
                        analysis.filterProbability.takeIf(::isValidProbability)
                            ?: return blocked(
                                payload,
                                DagOnDeviceImageAnalyzer.InvalidModelOutputReason,
                            )
                    is DagImageAnalysisResult.Unavailable ->
                        return blocked(payload, analysis.reason)
                }
            maximumProbability = maxOf(maximumProbability, regionalProbability)
            if (
                regionalProbability >=
                DagOnDeviceImageAnalyzer.RegionalFilterThreshold
            ) {
                return blocked(
                    payload,
                    DagOnDeviceImageAnalyzer.ModelFilterReason,
                    maximumProbability,
                )
            }
        }
        return DagMediaDecision(
            candidateId = payload.candidateId,
            action = DagMediaAction.Allow,
            reason = DagOnDeviceImageAnalyzer.ModelAllowReason,
            filterProbability = maximumProbability,
        )
    }

    private fun isValidProbability(probability: Float): Boolean = probability.isFinite() && probability in 0f..1f

    private fun blocked(
        payload: DagMediaBytesPayload,
        reason: String,
        filterProbability: Float? = null,
    ) = DagMediaDecision(
        candidateId = payload.candidateId.take(MaxCandidateIdLength),
        action = DagMediaAction.Block,
        reason = reason,
        filterProbability = filterProbability,
    )

    private fun isAllowedUrl(value: String): Boolean {
        if (value.length !in 1..MaxUrlLength) return false
        return runCatching { URI(value).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)
    }

    private const val MaxCandidateIdLength = 80
    private const val MaxUrlLength = 4_096
    const val MaxCaptureBytes = 2 * 1024 * 1024
    private const val MaxBase64Length = ((MaxCaptureBytes + 2) / 3) * 4
    const val InvalidPayloadReason = "invalid_payload"
    const val SafeUiVectorReason = "safe_ui_vector"
    const val UnsupportedImageReason = "unsupported_image"
    const val UnsafeDimensionsReason = "unsafe_dimensions"
    const val AnalyzerBusyReason = "analyzer_busy"
}
