package com.contentfilter.dagbrowser

import java.net.URI

internal data class DagMediaCandidate(
    val candidateId: String,
    val sourceUrl: String,
    val documentUrl: String,
    val altText: String,
    val width: Int,
    val height: Int,
)

internal data class DagMediaDecision(
    val candidateId: String,
    val action: DagMediaAction,
    val reason: String,
)

internal enum class DagMediaAction(
    val wireValue: String,
) {
    Block("block"),
}

/**
 * Fail-closed boundary for the future on-device classifier.
 *
 * The browser extension may discover media now, but this policy must keep it
 * blocked until a measured and signed local model is integrated.
 */
internal object DagMediaAnalysisPolicy {
    private val candidateIdPattern = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val allowedSchemes = setOf("http", "https")

    fun decide(candidate: DagMediaCandidate): DagMediaDecision {
        val valid =
            candidateIdPattern.matches(candidate.candidateId) &&
                isAllowedUrl(candidate.sourceUrl) &&
                isAllowedUrl(candidate.documentUrl) &&
                candidate.altText.length <= MaxAltTextLength &&
                candidate.width in 0..MaxDimension &&
                candidate.height in 0..MaxDimension

        return DagMediaDecision(
            candidateId = candidate.candidateId.take(MaxCandidateIdLength),
            action = DagMediaAction.Block,
            reason = if (valid) AnalyzerUnavailableReason else InvalidCandidateReason,
        )
    }

    private fun isAllowedUrl(value: String): Boolean {
        if (value.length !in 1..MaxUrlLength) return false
        return runCatching { URI(value).scheme?.lowercase() in allowedSchemes }.getOrDefault(false)
    }

    private const val MaxCandidateIdLength = 80
    private const val MaxUrlLength = 4_096
    private const val MaxAltTextLength = 256
    private const val MaxDimension = 32_768
    const val AnalyzerUnavailableReason = "analyzer_unavailable"
    const val InvalidCandidateReason = "invalid_candidate"
}
