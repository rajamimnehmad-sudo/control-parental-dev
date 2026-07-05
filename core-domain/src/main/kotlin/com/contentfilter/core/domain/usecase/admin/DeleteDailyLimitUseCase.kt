package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.repository.DailyLimitRepository

class DeleteDailyLimitUseCase(
    private val repository: DailyLimitRepository,
) {
    suspend operator fun invoke(limit: DailyLimit) {
        repository.deleteLimit(limit)
    }
}
