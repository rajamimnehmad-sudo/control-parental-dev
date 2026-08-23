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
        "localDecisionMs=${"%.3f".format(Locale.US, timings.totalLocalMs)} "
}
