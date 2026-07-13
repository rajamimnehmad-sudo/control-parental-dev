package com.contentfilter.core.domain.model

/**
 * A source-independent web classification result.
 *
 * The decision contains no domain, URL, query, or page content so it can be
 * passed between policy layers without expanding the data each layer sees.
 */
data class WebPolicyDecision(
    val outcome: WebPolicyOutcome,
    val category: String,
    val confidence: Double,
    val source: WebPolicyDecisionSource,
    val version: Long,
    val technicalReason: String,
    val evaluatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
) {
    init {
        require(category.isNotBlank()) { "Category must not be blank." }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "Confidence must be between 0 and 1."
        }
        require(version >= 0L) { "Version must not be negative." }
        require(technicalReason.isNotBlank()) { "Technical reason must not be blank." }
        require(evaluatedAtEpochMillis >= 0L) { "Evaluation time must not be negative." }
        require(expiresAtEpochMillis == null || expiresAtEpochMillis > evaluatedAtEpochMillis) {
            "Expiry must be later than the evaluation time."
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = expiresAtEpochMillis?.let { nowEpochMillis >= it } == true
}

enum class WebPolicyOutcome {
    Allow,
    Block,
    Uncertain,
    RequireReview,
}

/** Sources planned by the web-protection epic; declaring one does not activate it. */
enum class WebPolicyDecisionSource {
    PlatformPolicy,
    AdministratorRule,
    TechnicalAllowlist,
    SignedDomainList,
    LocalDomainClassifier,
    LocalSearchClassifier,
    DefaultPolicy,
}
