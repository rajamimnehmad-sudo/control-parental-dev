package com.contentfilter.core.data

import com.contentfilter.core.database.entity.UsageSessionEntity
import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.model.UsageSession

internal fun UsageSessionEntity.toDomain(): UsageSession =
    UsageSession(
        id = id,
        deviceId = deviceId,
        packageName = packageName,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
    )

internal fun UsageSession.toEntity(): UsageSessionEntity =
    UsageSessionEntity(
        id = id,
        deviceId = deviceId,
        packageName = packageName,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
    )

internal fun dailyAppUsage(
    packageName: String,
    deviceId: String,
    localDate: String,
    usedMillis: Long,
): DailyAppUsage =
    DailyAppUsage(
        packageName = packageName,
        deviceId = deviceId,
        localDate = localDate,
        usedMinutes = (usedMillis / MillisPerMinute).toInt(),
    )

private const val MillisPerMinute = 60_000L
