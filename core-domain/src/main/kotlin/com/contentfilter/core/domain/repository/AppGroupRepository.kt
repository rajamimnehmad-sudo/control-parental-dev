package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.AppGroup
import kotlinx.coroutines.flow.Flow

interface AppGroupRepository {
    fun observeGroups(deviceId: String? = null): Flow<List<AppGroup>>

    suspend fun saveGroup(group: AppGroup)

    suspend fun deleteGroup(group: AppGroup)

    suspend fun replaceGroupApps(
        group: AppGroup,
        packageNames: List<String>,
    )
}
