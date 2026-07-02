package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.RequestStatus
import kotlinx.coroutines.flow.Flow

/**
 * Stores local unlock and extra-time requests.
 */
interface AccessRequestRepository {
    fun observeRequests(): Flow<List<AccessRequest>>

    fun observePendingRequests(): Flow<List<AccessRequest>>

    suspend fun saveRequest(request: AccessRequest)

    suspend fun updateStatus(
        requestId: String,
        status: RequestStatus,
    )
}
