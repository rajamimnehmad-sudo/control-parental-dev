package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.UsageSessionRepository

/**
 * Saves a usage session captured by a platform integration.
 */
class SaveUsageSessionUseCase(
    private val repository: UsageSessionRepository,
) {
    suspend operator fun invoke(session: UsageSession) {
        repository.saveSession(session)
    }
}
