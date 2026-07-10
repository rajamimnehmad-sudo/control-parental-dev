package com.contentfilter.core.sync.engine

data class PolicyFastSyncResult(
    val localSaved: Boolean,
    val serverConfirmed: Boolean,
    val notificationDelivered: Boolean,
    val policyId: String,
    val revision: Long,
    val pendingOperationIds: List<String>,
    val error: String? = null,
)

data class PolicyPullResult(
    val success: Boolean,
    val requestId: String,
    val deviceId: String,
    val policyId: String?,
    val revision: Long?,
    val roomApplied: Boolean,
    val error: String? = null,
)

enum class PolicyApplicationState {
    Applied,
    Pending,
    Offline,
}

data class PolicyApplicationResult(
    val state: PolicyApplicationState,
    val revision: Long,
    val appliedAtEpochMillis: Long? = null,
)
