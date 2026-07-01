package com.contentfilter.core.domain.model

/**
 * Immutable policy rule used by the domain layer.
 */
data class PolicyRule(
    val id: String,
    val level: PolicyLevel = PolicyLevel.Global,
    val scope: RuleScope,
    val target: String,
    val action: RuleAction,
    val priority: Int,
    val enabled: Boolean,
    val activeWindow: PolicyTimeWindow? = null,
    val safeSearchRequired: Boolean = false,
)
