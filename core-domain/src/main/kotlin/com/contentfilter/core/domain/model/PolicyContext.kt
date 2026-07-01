package com.contentfilter.core.domain.model

/**
 * Context sent by platform integrations to the PolicyEngine.
 */
data class PolicyContext(
    val packageName: String?,
    val domain: String?,
    val healthSnapshot: SystemHealthSnapshot,
    val evaluatedAtEpochMillis: Long,
)
