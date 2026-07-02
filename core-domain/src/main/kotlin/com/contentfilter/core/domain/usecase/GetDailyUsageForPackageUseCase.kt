package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.repository.UsageSessionRepository

class GetDailyUsageForPackageUseCase(
    private val repository: UsageSessionRepository,
) {
    suspend operator fun invoke(
        deviceId: String,
        packageName: String,
        localDate: String,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): DailyAppUsage =
        repository.dailyUsageForPackage(
            deviceId = deviceId,
            packageName = packageName,
            localDate = localDate,
            dayStartEpochMillis = dayStartEpochMillis,
            dayEndEpochMillis = dayEndEpochMillis,
        )
}
