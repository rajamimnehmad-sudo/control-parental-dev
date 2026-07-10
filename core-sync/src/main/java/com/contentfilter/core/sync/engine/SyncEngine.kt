package com.contentfilter.core.sync.engine

import com.contentfilter.core.domain.model.PolicyMutationReceipt

interface SyncEngine {
    suspend fun syncOnce(): SyncResult

    suspend fun syncCoreDataFull(): SyncResult

    suspend fun syncDevicesFull(): SyncResult

    suspend fun syncAccessRequestsFull(): SyncResult

    suspend fun syncRequestResultsFull(): SyncResult

    suspend fun syncPolicyChanges(receipt: PolicyMutationReceipt): PolicyFastSyncResult

    suspend fun pullPolicyRevision(
        requestId: String,
        deviceId: String,
        policyId: String? = null,
        minimumRevision: Long? = null,
        reason: String,
    ): PolicyPullResult

    suspend fun acknowledgePolicyApplied(
        requestId: String,
        deviceId: String,
        policyId: String,
        revision: Long,
    ): Boolean

    suspend fun waitForPolicyApplied(
        receipt: PolicyMutationReceipt,
        timeoutMillis: Long,
    ): PolicyApplicationResult
}
