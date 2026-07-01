package com.contentfilter.core.domain.model

/**
 * Daily usage limit for a policy target.
 */
data class DailyLimit(
    val id: String,
    val targetType: PolicyTargetType,
    val target: String,
    val limitMinutes: Int,
    val enabled: Boolean,
)
