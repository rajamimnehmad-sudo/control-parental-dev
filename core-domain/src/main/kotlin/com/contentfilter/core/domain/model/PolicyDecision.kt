package com.contentfilter.core.domain.model

/**
 * Final decision returned by the PolicyEngine.
 */
sealed interface PolicyDecision {
    data class Allow(val safeSearchRequired: Boolean = false) : PolicyDecision
    data class Block(val reason: String) : PolicyDecision
    data class Warn(val message: String) : PolicyDecision
    data class RequestAuthorization(val resource: String) : PolicyDecision
    data class GrantExtraTime(val minutes: Int, val validUntilEpochMillis: Long) : PolicyDecision
    data class RequireUpdate(val reason: String) : PolicyDecision
    data class RequireActivation(val reason: String) : PolicyDecision
    data class HealthWarning(val message: String) : PolicyDecision
}
