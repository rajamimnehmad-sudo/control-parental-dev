package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.ExtraTimeGrant
import kotlinx.coroutines.flow.Flow

interface ExtraTimeGrantRepository {
    fun observeActiveGrants(nowEpochMillis: Long): Flow<List<ExtraTimeGrant>>

    fun observeGrants(): Flow<List<ExtraTimeGrant>>

    suspend fun saveGrant(grant: ExtraTimeGrant)
}
