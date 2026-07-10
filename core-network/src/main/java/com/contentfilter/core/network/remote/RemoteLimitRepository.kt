package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto

interface RemoteLimitRepository {
    suspend fun pullDailyLimits(updatedAfterIso: String?): RemoteResult<List<RemoteDailyLimitDto>>

    suspend fun pullDailyLimitsForPolicy(policyId: String): RemoteResult<List<RemoteDailyLimitDto>>

    suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto): RemoteResult<Unit>

    suspend fun pullAppGroups(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupDto>>

    suspend fun pullAppGroupsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupDto>>

    suspend fun pullAppGroupApps(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupAppDto>>

    suspend fun pullAppGroupAppsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupAppDto>>

    suspend fun upsertAppGroup(group: RemoteAppGroupDto): RemoteResult<Unit>

    suspend fun upsertAppGroupApp(app: RemoteAppGroupAppDto): RemoteResult<Unit>
}
