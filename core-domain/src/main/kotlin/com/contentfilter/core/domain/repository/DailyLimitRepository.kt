package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import kotlinx.coroutines.flow.Flow

/**
 * Stores local daily limits used by the PolicyEngine.
 */
interface DailyLimitRepository {
    fun observeLimits(deviceId: String? = null): Flow<List<DailyLimit>>

    suspend fun saveLimit(
        limit: DailyLimit,
        deviceId: String? = null,
        requestId: String? = null,
    ): PolicyMutationReceipt

    suspend fun deleteLimit(
        limit: DailyLimit,
        deviceId: String? = null,
        requestId: String? = null,
    ): PolicyMutationReceipt
}
