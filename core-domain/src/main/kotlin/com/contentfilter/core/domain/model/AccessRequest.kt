package com.contentfilter.core.domain.model

/**
 * User request to unlock a target or ask for extra time.
 */
data class AccessRequest(
    val id: String,
    val requestType: AccessRequestType,
    val targetType: PolicyTargetType,
    val target: String,
    val targetPackageName: String?,
    val targetDomain: String?,
    val reason: String,
    val requestedMinutes: Int?,
    val status: RequestStatus,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val deviceId: String? = null,
)
