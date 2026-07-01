package com.contentfilter.feature.accessibility.policy

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface LocalDayProvider {
    fun currentDay(): LocalDay
}

class SystemLocalDayProvider
    @Inject
    constructor() : LocalDayProvider {
        override fun currentDay(): LocalDay {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return LocalDay(
                localDate = today.toString(),
                startEpochMillis = start,
                endEpochMillis = end,
            )
        }
    }
