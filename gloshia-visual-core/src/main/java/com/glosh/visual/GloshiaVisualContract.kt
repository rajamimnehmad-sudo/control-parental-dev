package com.glosh.visual

/** Frozen public identity of the production visual engine. */
object GloshiaVisualModelInfo {
    const val PublicName = "GloshIA Visual"
    const val FunctionalVersion = "R3.1"
    const val ModelAssetPath = "dag-model/tinyclip-r3-head-hybrid-int8.onnx"
    const val ModelSha256 =
        "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
    const val Runtime = "ONNX Runtime Android 1.27.0"
    const val PolicyVersion = "dag-36"
    const val ShortSha256 = "c8b64af8…a3cd48"
}

object GloshiaVisualPolicyContract {
    const val FilterThreshold = 0.4f
    const val FullStrongFilterThreshold = 0.95f
    const val UncertainRegionalReviewFloor = 0.3f
    const val UncertainRegionalFilterThreshold = 0.45f
    const val RegionalFilterThreshold = 0.5f
    const val RegionalStrongFilterThreshold = 0.7f
    const val RegionalConsensusMinimum = 2

    const val ModelAllowReason = "model_allow"
    const val ModelFilterReason = "model_filter"
    const val InvalidModelInputReason = "invalid_model_input"
    const val InvalidModelOutputReason = "invalid_model_output"
    const val ModelExecutionFailedReason = "model_execution_failed"
    const val AnalyzerUnavailableReason = "analyzer_unavailable"
    const val AnalyzerClosedReason = "analyzer_closed"
    const val DecodeFailedReason = "decode_failed"
    const val AnimatedImageReason = "animated_image"
    const val AnalysisExpiredReason = "analysis_expired"
}

data class GloshiaPreparedImage(
    val width: Int,
    val height: Int,
    val rgb888: ByteArray,
)

object GloshiaImageContract {
    val SupportedMimeTypes =
        setOf(
            "image/avif",
            "image/bmp",
            "image/gif",
            "image/heic",
            "image/heif",
            "image/ico",
            "image/jpeg",
            "image/png",
            "image/vnd.microsoft.icon",
            "image/webp",
            "image/x-icon",
        )
    const val TargetSize = 224
    const val RgbChannelCount = 3
    const val MaxDimension = 4_096
    const val MaxPixels = 16_777_216L
    const val PreparedByteCount = TargetSize * TargetSize * RgbChannelCount

    fun hasSafeDimensions(
        width: Int,
        height: Int,
    ): Boolean =
        width in 1..MaxDimension &&
            height in 1..MaxDimension &&
            width.toLong() * height.toLong() <= MaxPixels

    fun isValid(image: GloshiaPreparedImage): Boolean =
        image.width == TargetSize &&
            image.height == TargetSize &&
            image.rgb888.size == PreparedByteCount
}

enum class GloshiaVisualAction { Allow, Block }

enum class GloshiaVisualDecisionBasis {
    None,
    FullThreshold,
    FullStrong,
    UncertainRegional,
    RegionalStrong,
    RegionalConsensus,
}

data class GloshiaVisualDecision(
    val candidateId: String,
    val action: GloshiaVisualAction,
    val reason: String,
    val filterProbability: Float? = null,
    val basis: GloshiaVisualDecisionBasis = GloshiaVisualDecisionBasis.None,
    val preparedImageCount: Int = 0,
    val regionalImageCount: Int = 0,
    val fullImageProbability: Float? = null,
)
