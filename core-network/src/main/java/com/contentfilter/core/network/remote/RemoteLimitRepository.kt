package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteDailyLimitDto

interface RemoteLimitRepository {
    suspend fun pullDailyLimits(updatedAfterIso: String?): RemoteResult<List<RemoteDailyLimitDto>>

    suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto): RemoteResult<Unit>
}
