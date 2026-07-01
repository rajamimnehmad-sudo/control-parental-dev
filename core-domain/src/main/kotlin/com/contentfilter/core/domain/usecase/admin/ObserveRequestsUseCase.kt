package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.repository.AccessRequestRepository

class ObserveRequestsUseCase(
    private val repository: AccessRequestRepository,
) {
    operator fun invoke() = repository.observeRequests()
}
