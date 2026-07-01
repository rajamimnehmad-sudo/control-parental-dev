package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.repository.SystemStatusRepository

/**
 * Observes protection health for UI state.
 */
class ObserveSystemHealthUseCase(
    private val repository: SystemStatusRepository,
) {
    operator fun invoke() = repository.observeHealth()
}
