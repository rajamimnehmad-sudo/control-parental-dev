package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import javax.inject.Inject

class SupabaseRemoteLimitRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteLimitRepository {
        override suspend fun pullDailyLimits(updatedAfterIso: String?): RemoteResult<List<RemoteDailyLimitDto>> =
            client.selectUpdatedSince(
                SupabaseTable.DailyLimits,
                updatedAfterIso,
            ).mapArray(RemoteDailyLimitDto::fromJson)

        override suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.DailyLimits, limit.toJson())

        override suspend fun pullAppGroups(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupDto>> =
            client.selectUpdatedSince(
                SupabaseTable.AppGroups,
                updatedAfterIso,
            ).mapArray(RemoteAppGroupDto::fromJson)

        override suspend fun pullAppGroupApps(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupAppDto>> =
            client.selectUpdatedSince(
                SupabaseTable.AppGroupApps,
                updatedAfterIso,
            ).mapArray(RemoteAppGroupAppDto::fromJson)

        override suspend fun upsertAppGroup(group: RemoteAppGroupDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.AppGroups, group.toJson())

        override suspend fun upsertAppGroupApp(app: RemoteAppGroupAppDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.AppGroupApps, app.toJson())
    }
