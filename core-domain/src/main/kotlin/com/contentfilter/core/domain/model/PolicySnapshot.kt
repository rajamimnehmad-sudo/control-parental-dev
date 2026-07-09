package com.contentfilter.core.domain.model

/**
 * In-memory policy snapshot optimized for fast PolicyEngine evaluation.
 */
data class PolicySnapshot(
    val id: String,
    val deviceId: String? = null,
    val version: Long,
    val rules: List<PolicyRule>,
    val dailyLimits: List<DailyLimit> = emptyList(),
    val dailyUsage: List<DailyAppUsage> = emptyList(),
    val extraTimeGrants: List<ExtraTimeGrant> = emptyList(),
    val appGroups: List<AppGroup> = emptyList(),
)
