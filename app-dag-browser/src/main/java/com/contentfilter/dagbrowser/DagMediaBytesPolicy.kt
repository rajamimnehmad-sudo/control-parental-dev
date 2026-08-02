package com.contentfilter.dagbrowser

import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.zip.GZIPInputStream

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

internal enum class DagMediaClassificationMode {
    Enabled,
    DisabledForDevCompatibility,
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
        classificationMode: DagMediaClassificationMode = DagMediaClassificationMode.Enabled,
        trace: DagMediaPipelineTrace? = null,
        workGuard: DagMediaWorkGuard = AlwaysCurrentDagMediaWork,
    ): DagMediaDecision {
        if (!validEnvelope(payload)) return blocked(payload, InvalidPayloadReason)
        if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
        return inspectImage(
            payload,
            boundsReader,
            preprocessor,
            analyzer,
            classificationMode,
            trace,
            workGuard,
        )
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
        classificationMode: DagMediaClassificationMode,
        trace: DagMediaPipelineTrace?,
        workGuard: DagMediaWorkGuard,
    ): DagMediaDecision {
        if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
        val bytes =
            runCatching {
                trace.measure(DagMediaPipelineStage.Base64Decode) {
                    Base64.getDecoder().decode(payload.bytesBase64)
                }
            }
                .getOrElse { return blocked(payload, InvalidPayloadReason) }
        var analysisBytes = bytes
        try {
            if (bytes.size != payload.declaredByteLength || bytes.size > MaxCaptureBytes) {
                return blocked(payload, InvalidPayloadReason)
            }
            if (classificationMode == DagMediaClassificationMode.DisabledForDevCompatibility) {
                return DagMediaDecision(
                    candidateId = payload.candidateId,
                    action = DagMediaAction.Allow,
                    reason = DevClassifierBypassReason,
                )
            }
            analysisBytes = decodeTransportBytes(bytes) ?: return blocked(payload, UnsupportedImageReason)
            if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
            val safeUiVector =
                trace.measure(DagMediaPipelineStage.SafeVectorCheck) {
                    DagSafeUiVectorPolicy.isSafe(analysisBytes)
                }
            if (safeUiVector) {
                if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
                return DagMediaDecision(
                    candidateId = payload.candidateId,
                    action = DagMediaAction.Allow,
                    reason = SafeUiVectorReason,
                )
            }
            if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
            val bounds =
                trace.measure(DagMediaPipelineStage.BoundsRead) { boundsReader.read(analysisBytes) }
                    ?: return blocked(payload, UnsupportedImageReason)
            if (bounds.mimeType !in DagImageDecodeContract.SupportedMimeTypes) {
                return blocked(payload, UnsupportedImageReason)
            }
            if (!DagImageDecodeContract.hasSafeDimensions(bounds.width, bounds.height)) {
                return blocked(payload, UnsafeDimensionsReason)
            }
            if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
            return when (
                val result =
                    runCatching {
                        trace.measure(DagMediaPipelineStage.Preprocess) {
                            preprocessor.prepare(analysisBytes)
                        }
                    }
                        .getOrElse {
                            DagImagePreprocessResult.Rejected(
                                AndroidDagImagePreprocessor.DecodeFailedReason,
                            )
                        }
            ) {
                is DagImagePreprocessResult.Ready -> {
                    val preparedImages = listOf(result.image) + result.regionalImages
                    trace?.preparedImageCount = preparedImages.size
                    trace?.regionalImageCount = result.regionalImages.size
                    val valid = preparedImages.all(DagImageDecodeContract::isValid)
                    try {
                        if (!valid) {
                            blocked(payload, AndroidDagImagePreprocessor.DecodeFailedReason)
                        } else {
                            decidePreparedImages(payload, preparedImages, analyzer, trace, workGuard)
                        }
                    } catch (_: Exception) {
                        blocked(payload, DagOnDeviceImageAnalyzer.ModelExecutionFailedReason)
                    } finally {
                        preparedImages.forEach { it.rgb888.fill(0) }
                    }
                }
                is DagImagePreprocessResult.Rejected -> blocked(payload, result.reason)
            }.copy(imageWidth = bounds.width, imageHeight = bounds.height)
        } finally {
            if (analysisBytes !== bytes) analysisBytes.fill(0)
            bytes.fill(0)
        }
    }

    private fun decodeTransportBytes(bytes: ByteArray): ByteArray? {
        val isGzip =
            bytes.size >= 2 &&
                bytes[0] == GzipMagicFirst &&
                bytes[1] == GzipMagicSecond
        if (!isGzip) return bytes
        return runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val output = ByteArrayOutputStream(minOf(bytes.size * 2, MaxCaptureBytes))
                val buffer = ByteArray(16 * 1024)
                var decodedBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    decodedBytes += count
                    if (decodedBytes > MaxCaptureBytes) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray().takeIf(ByteArray::isNotEmpty)
            }
        }.getOrNull()
    }

    private fun decidePreparedImages(
        payload: DagMediaBytesPayload,
        preparedImages: List<DagPreparedImage>,
        analyzer: DagImageAnalyzer,
        trace: DagMediaPipelineTrace?,
        workGuard: DagMediaWorkGuard,
    ): DagMediaDecision {
        if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
        val fullProbability =
            when (
                val analysis =
                    trace.measureInference { analyzer.analyze(preparedImages.first()) }
            ) {
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

        if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)

        val preparedRegionalImages = preparedImages.drop(1)
        val generatedUncertainRegions =
            if (
                preparedRegionalImages.isEmpty() &&
                fullProbability >= DagOnDeviceImageAnalyzer.UncertainRegionalReviewFloor
            ) {
                DagUncertainRegionalCropper.quadrantViews(preparedImages.first())
            } else {
                emptyList()
            }
        val regionalImages = preparedRegionalImages.ifEmpty { generatedUncertainRegions }
        if (generatedUncertainRegions.isNotEmpty()) {
            trace?.regionalImageCount = generatedUncertainRegions.size
            trace?.preparedImageCount = 1 + generatedUncertainRegions.size
        }
        return try {
            var maximumProbability = fullProbability
            var regionalFilterVotes = 0
            for (regionalImage in regionalImages) {
                if (!workGuard.canContinue()) {
                    return blocked(payload, AnalysisExpiredReason)
                }
                val regionalProbability =
                    when (val analysis = trace.measureInference { analyzer.analyze(regionalImage) }) {
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
                if (regionalProbability >= DagOnDeviceImageAnalyzer.RegionalFilterThreshold) {
                    regionalFilterVotes += 1
                }
                val uncertainRegionIsUnsafe =
                    generatedUncertainRegions.isNotEmpty() &&
                        regionalProbability >=
                        DagOnDeviceImageAnalyzer.UncertainRegionalFilterThreshold
                if (
                    uncertainRegionIsUnsafe ||
                    regionalProbability >= DagOnDeviceImageAnalyzer.RegionalStrongFilterThreshold ||
                    regionalFilterVotes >= DagOnDeviceImageAnalyzer.RegionalConsensusMinimum
                ) {
                    return blocked(
                        payload,
                        DagOnDeviceImageAnalyzer.ModelFilterReason,
                        maximumProbability,
                    )
                }
            }
            if (!workGuard.canContinue()) return blocked(payload, AnalysisExpiredReason)
            DagMediaDecision(
                candidateId = payload.candidateId,
                action = DagMediaAction.Allow,
                reason = DagOnDeviceImageAnalyzer.ModelAllowReason,
                filterProbability = maximumProbability,
            )
        } finally {
            generatedUncertainRegions.forEach { it.rgb888.fill(0) }
        }
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
    private const val GzipMagicFirst: Byte = 0x1f
    private const val GzipMagicSecond: Byte = -117
    private const val MaxBase64Length = ((MaxCaptureBytes + 2) / 3) * 4
    const val InvalidPayloadReason = "invalid_payload"
    const val SafeUiVectorReason = "safe_ui_vector"
    const val UnsupportedImageReason = "unsupported_image"
    const val UnsafeDimensionsReason = "unsafe_dimensions"
    const val AnalyzerBusyReason = "analyzer_busy"
    const val AnalysisExpiredReason = "analysis_expired"
    const val DevClassifierBypassReason = "classifier_bypassed_dev"
}

private fun <T> DagMediaPipelineTrace?.measure(
    stage: DagMediaPipelineStage,
    operation: () -> T,
): T = this?.measure(stage, operation) ?: operation()

private fun <T> DagMediaPipelineTrace?.measureInference(operation: () -> T): T =
    this?.measureInference(operation) ?: operation()
