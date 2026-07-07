package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAccountDto
import javax.inject.Inject

class SupabaseRemoteAccountRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteAccountRepository {
        override suspend fun pullAccounts(updatedAfterIso: String?): RemoteResult<List<RemoteAccountDto>> =
            client.selectAccounts(updatedAfterIso).mapArray(RemoteAccountDto::fromJson)
    }
