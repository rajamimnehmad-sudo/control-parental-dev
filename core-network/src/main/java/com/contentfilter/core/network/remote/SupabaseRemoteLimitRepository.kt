package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import javax.inject.Inject

class SupabaseRemoteLimitRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteLimitRepository {
        override suspend fun pullDailyLimits(updatedAfterIso: String?): RemoteResult<List<RemoteDailyLimitDto>> =
            client.selectUpdatedSince(SupabaseTable.DailyLimits, updatedAfterIso).mapArray(RemoteDailyLimitDto::fromJson)

        override suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.DailyLimits, limit.toJson())
    }
