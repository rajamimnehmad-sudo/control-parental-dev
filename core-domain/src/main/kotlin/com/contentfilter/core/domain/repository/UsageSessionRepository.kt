package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.model.UsageSession
import kotlinx.coroutines.flow.Flow

/**
 * Records app usage sessions for local time limits.
 */
interface UsageSessionRepository {
    fun observeSessions(packageName: String): Flow<List<UsageSession>>

    fun observeDailyUsage(
        deviceId: String,
        localDate: String,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): Flow<List<DailyAppUsage>>

    suspend fun saveSession(session: UsageSession)

    suspend fun dailyUsageForPackage(
        deviceId: String,
        packageName: String,
        localDate: String,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): DailyAppUsage
}
