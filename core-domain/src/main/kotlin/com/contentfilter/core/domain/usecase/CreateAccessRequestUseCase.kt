package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.repository.AccessRequestRepository

/**
 * Creates an offline-first access request.
 */
class CreateAccessRequestUseCase(
    private val repository: AccessRequestRepository,
) {
    suspend operator fun invoke(request: AccessRequest) {
        repository.saveRequest(request)
    }
}
