package com.contentfilter.feature.accessibility.chromevisual

/**
 * Structural WebView evidence used only while H19 owns opaque presentation barriers.
 *
 * Android may report Chrome's exact WebView root as not visible while the accessibility surface
 * covers the browser. Visibility is therefore diagnostic here, never identity. Package, class,
 * window, browser-issued unique id and actual ancestry under the current native root remain
 * mandatory. The bounded traversal still rejects zero or multiple candidates.
 */
internal data class ChromeMediaShieldAttachedWebRootEvidence(
    val candidate: ChromeMediaShieldWebRootCandidateEvidence,
    val candidateVisibleToUser: Boolean,
    val candidateAttachedToWindowRoot: Boolean,
)

internal object ChromeMediaShieldAttachedWebRootPolicy {
    fun verifies(evidence: ChromeMediaShieldAttachedWebRootEvidence): Boolean =
        ChromeMediaShieldWebRootCandidatePolicy.verifies(evidence.candidate) &&
            evidence.candidateAttachedToWindowRoot
}
