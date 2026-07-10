package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import kotlinx.coroutines.flow.Flow

interface AppGroupRepository {
    fun observeGroups(deviceId: String? = null): Flow<List<AppGroup>>

    suspend fun saveGroup(
        group: AppGroup,
        requestId: String? = null,
    ): PolicyMutationReceipt

    suspend fun deleteGroup(
        group: AppGroup,
        requestId: String? = null,
    ): PolicyMutationReceipt

    suspend fun replaceGroupApps(
        group: AppGroup,
        packageNames: List<String>,
        requestId: String? = null,
    ): PolicyMutationReceipt
}
