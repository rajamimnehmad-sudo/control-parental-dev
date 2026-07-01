package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto

interface RemoteRequestRepository {
    suspend fun pullAccessRequests(updatedAfterIso: String?): RemoteResult<List<RemoteAccessRequestDto>>

    suspend fun pullExtraTimeGrants(updatedAfterIso: String?): RemoteResult<List<RemoteExtraTimeGrantDto>>

    suspend fun upsertAccessRequest(request: RemoteAccessRequestDto): RemoteResult<Unit>

    suspend fun upsertExtraTimeGrant(grant: RemoteExtraTimeGrantDto): RemoteResult<Unit>
}
