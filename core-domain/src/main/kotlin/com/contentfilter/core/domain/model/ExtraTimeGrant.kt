package com.contentfilter.core.domain.model

data class ExtraTimeGrant(
    val id: String,
    val requestId: String?,
    val targetType: PolicyTargetType,
    val target: String,
    val grantedMinutes: Int,
    val validUntilEpochMillis: Long,
)
