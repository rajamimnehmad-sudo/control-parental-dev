package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteAccountDto

interface RemoteAccountRepository {
    suspend fun pullAccounts(updatedAfterIso: String?): RemoteResult<List<RemoteAccountDto>>
}
