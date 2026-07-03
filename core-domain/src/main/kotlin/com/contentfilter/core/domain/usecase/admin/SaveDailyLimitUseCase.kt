package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.repository.DailyLimitRepository

class SaveDailyLimitUseCase(
    private val repository: DailyLimitRepository,
) {
    suspend operator fun invoke(limit: DailyLimit) {
        repository.saveLimit(limit)
    }
}
