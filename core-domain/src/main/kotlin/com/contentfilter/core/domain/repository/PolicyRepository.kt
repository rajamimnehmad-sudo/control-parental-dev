package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Source of the active local policy.
 */
interface PolicyRepository {
    fun observeActivePolicy(): Flow<PolicySnapshot>

    suspend fun getActivePolicy(): PolicySnapshot

    suspend fun saveRule(rule: PolicyRule)
}
