package com.contentfilter.user.chromedataplane

import java.util.Locale

internal fun String?.safeLogContentType(): String =
    this
        ?.substringBefore(';')
        ?.lowercase(Locale.US)
        ?.filter { character -> character.isLetterOrDigit() || character in "/+.-" }
        ?.take(64)
        .orEmpty()

internal fun ChromePhotoDecisionResult?.logFields(): String {
    if (this == null) return ""
    return "reason=${reason.take(48)} source=${source.name.lowercase(Locale.US)} " +
        "probability=${filterProbability ?: -1f} basis=${basis.take(32)} " +
        "decodeMs=${"%.3f".format(Locale.US, timings.decodeAndPreprocessMs)} " +
        "inferenceMs=${"%.3f".format(Locale.US, timings.inferenceMs)} " +
        "inferenceQueueMs=${"%.3f".format(Locale.US, timings.queueWaitMs)} " +
        "localDecisionMs=${"%.3f".format(Locale.US, timings.totalLocalMs)} "
}

internal fun ChromePhotosSanitizedResponse.imagePhaseFields(): String =
    listOf("bodyAdmissionMs" to bodyAdmissionMs, "bodyReadMs" to bodyReadMs, "hashMs" to hashMs)
        .mapNotNull { (name, value) -> value?.let { "$name=${"%.3f".format(Locale.US, it)}" } }
        .joinToString(" ")
