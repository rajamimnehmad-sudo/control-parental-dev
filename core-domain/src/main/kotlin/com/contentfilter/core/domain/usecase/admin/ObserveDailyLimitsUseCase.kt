package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.repository.DailyLimitRepository

class ObserveDailyLimitsUseCase(
    private val repository: DailyLimitRepository,
) {
    operator fun invoke() = repository.observeLimits()
}
