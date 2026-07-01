package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import javax.inject.Inject

class SupabaseRemoteRequestRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteRequestRepository {
        override suspend fun pullAccessRequests(updatedAfterIso: String?): RemoteResult<List<RemoteAccessRequestDto>> =
            client.selectUpdatedSince(SupabaseTable.AccessRequests, updatedAfterIso)
                .mapArray(RemoteAccessRequestDto::fromJson)

        override suspend fun pullExtraTimeGrants(updatedAfterIso: String?): RemoteResult<List<RemoteExtraTimeGrantDto>> =
            client.selectUpdatedSince(SupabaseTable.ExtraTimeGrants, updatedAfterIso)
                .mapArray(RemoteExtraTimeGrantDto::fromJson)

        override suspend fun upsertAccessRequest(request: RemoteAccessRequestDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.AccessRequests, request.toJson())

        override suspend fun upsertExtraTimeGrant(grant: RemoteExtraTimeGrantDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.ExtraTimeGrants, grant.toJson())
    }
