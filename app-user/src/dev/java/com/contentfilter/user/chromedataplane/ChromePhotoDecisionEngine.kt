package com.contentfilter.user.chromedataplane

import java.util.Locale

internal enum class ChromePhotoDecision {
    Safe,
    Block,
    Unknown,
}

internal enum class ChromePhotoDecisionSource {
    Engine,
    Cache,
    InFlight,
    QueueFull,
    Timeout,
    Unavailable,
    Error,
}

internal data class ChromePhotoDecisionIdentity(
    val engine: String,
    val modelVersion: String,
    val modelSha256: String,
    val policyVersion: String,
) {
    val cacheKey: String = "$engine:$modelVersion:$modelSha256:$policyVersion"
}

internal data class ChromePhotoDecisionTimings(
    val decodeAndPreprocessMs: Double = 0.0,
    val inferenceMs: Double = 0.0,
    val totalLocalMs: Double = 0.0,
)

internal data class ChromePhotoDecisionResult(
    val decision: ChromePhotoDecision,
    val reason: String,
    val source: ChromePhotoDecisionSource = ChromePhotoDecisionSource.Engine,
    val filterProbability: Float? = null,
    val basis: String = "none",
    val preparedImageCount: Int = 0,
    val timings: ChromePhotoDecisionTimings = ChromePhotoDecisionTimings(),
)

internal interface ChromePhotoDecisionEngine : AutoCloseable {
    val identity: ChromePhotoDecisionIdentity

    fun decide(
        imageBytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult

    override fun close() = Unit
}

internal fun String.normalizedImageMimeType(): String =
    lowercase(Locale.US)
        .substringBefore(';')
        .trim()
