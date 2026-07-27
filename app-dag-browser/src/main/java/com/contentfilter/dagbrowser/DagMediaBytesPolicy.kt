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
 * Validates a bounded image response before a future classifier can decode pixels.
 *
 * Passing this boundary never means that media is safe. The only possible
 * decision remains block until a benchmarked model is connected.
 */
internal object DagMediaBytesPolicy {
    private val candidateIdPattern = Regex("^[A-Za-z0-9_-]{1,80}$")

    fun decide(
        payload: DagMediaBytesPayload,
        boundsReader: DagImageBoundsReader = AndroidImageBoundsReader,
        preprocessor: DagImagePreprocessor = AndroidDagImagePreprocessor,
    ): DagMediaDecision {
        val reason =
            when {
                !validEnvelope(payload) -> InvalidPayloadReason
                else -> inspectImage(payload, boundsReader, preprocessor)
            }
        return DagMediaDecision(
            candidateId = payload.candidateId.take(MaxCandidateIdLength),
            action = DagMediaAction.Block,
            reason = reason,
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
    ): String {
        val bytes =
            runCatching { Base64.getDecoder().decode(payload.bytesBase64) }
                .getOrElse { return InvalidPayloadReason }
        if (bytes.size != payload.declaredByteLength || bytes.size > MaxCaptureBytes) {
            return InvalidPayloadReason
        }
        val bounds = boundsReader.read(bytes) ?: return UnsupportedImageReason
        if (bounds.mimeType !in DagImageDecodeContract.SupportedMimeTypes) return UnsupportedImageReason
        if (!DagImageDecodeContract.hasSafeDimensions(bounds.width, bounds.height)) {
            return UnsafeDimensionsReason
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
                val valid = DagImageDecodeContract.isValid(result.image)
                result.image.rgb888.fill(0)
                if (valid) {
                    DagMediaAnalysisPolicy.AnalyzerUnavailableReason
                } else {
                    AndroidDagImagePreprocessor.DecodeFailedReason
                }
            }
            is DagImagePreprocessResult.Rejected -> result.reason
        }
    }

    private fun isAllowedUrl(value: String): Boolean {
        if (value.length !in 1..MaxUrlLength) return false
        return runCatching { URI(value).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)
    }

    private const val MaxCandidateIdLength = 80
    private const val MaxUrlLength = 4_096
    const val MaxCaptureBytes = 256 * 1024
    private const val MaxBase64Length = ((MaxCaptureBytes + 2) / 3) * 4
    const val InvalidPayloadReason = "invalid_payload"
    const val UnsupportedImageReason = "unsupported_image"
    const val UnsafeDimensionsReason = "unsafe_dimensions"
    const val AnalyzerBusyReason = "analyzer_busy"
}
