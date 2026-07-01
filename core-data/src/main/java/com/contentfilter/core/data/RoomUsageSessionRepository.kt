package com.contentfilter.core.data

import com.contentfilter.core.database.dao.UsageSessionDao
import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.UsageSessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUsageSessionRepository
    @Inject
    constructor(
        private val usageSessionDao: UsageSessionDao,
    ) : UsageSessionRepository {
        override fun observeSessions(packageName: String): Flow<List<UsageSession>> =
            usageSessionDao.observeForPackage(packageName).map { sessions -> sessions.map { it.toDomain() } }

        override fun observeDailyUsage(
            deviceId: String,
            localDate: String,
            dayStartEpochMillis: Long,
            dayEndEpochMillis: Long,
        ): Flow<List<DailyAppUsage>> =
            usageSessionDao.observeDailyUsage(deviceId, dayStartEpochMillis, dayEndEpochMillis).map { projections ->
                projections.map { projection ->
                    dailyAppUsage(
                        packageName = projection.packageName,
                        deviceId = deviceId,
                        localDate = localDate,
                        usedMillis = projection.usedMillis,
                    )
                }
            }

        override suspend fun saveSession(session: UsageSession) {
            usageSessionDao.upsert(session.toEntity())
        }

        override suspend fun dailyUsageForPackage(
            deviceId: String,
            packageName: String,
            localDate: String,
            dayStartEpochMillis: Long,
            dayEndEpochMillis: Long,
        ): DailyAppUsage {
            val projection = usageSessionDao.dailyUsageForPackage(
                deviceId = deviceId,
                packageName = packageName,
                dayStartEpochMillis = dayStartEpochMillis,
                dayEndEpochMillis = dayEndEpochMillis,
            )
            return dailyAppUsage(
                packageName = packageName,
                deviceId = deviceId,
                localDate = localDate,
                usedMillis = projection?.usedMillis ?: 0L,
            )
        }
    }
