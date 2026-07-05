package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Source of the active local policy.
 */
interface PolicyRepository {
    fun observeActivePolicy(deviceId: String? = null): Flow<PolicySnapshot>

    suspend fun getActivePolicy(deviceId: String? = null): PolicySnapshot

    suspend fun saveRule(
        rule: PolicyRule,
        deviceId: String? = null,
    )

    suspend fun deleteRule(rule: PolicyRule)
}
