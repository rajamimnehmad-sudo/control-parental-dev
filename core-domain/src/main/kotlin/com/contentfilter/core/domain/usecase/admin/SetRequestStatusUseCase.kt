package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository

class SetRequestStatusUseCase(
    private val repository: AccessRequestRepository,
) {
    suspend operator fun invoke(
        requestId: String,
        status: RequestStatus,
    ) {
        repository.updateStatus(requestId, status)
    }
}
