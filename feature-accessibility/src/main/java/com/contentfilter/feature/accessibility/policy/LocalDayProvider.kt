package com.contentfilter.feature.accessibility.policy

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
            val now = LocalDateTime.now(zone)
            val today = now.toLocalDate()
            val resetTime = LocalTime.NOON
            val startDate = if (now.toLocalTime().isBefore(resetTime)) today.minusDays(1) else today
            val start = startDate.atTime(resetTime).atZone(zone).toInstant().toEpochMilli()
            val end = startDate.plusDays(1).atTime(resetTime).atZone(zone).toInstant().toEpochMilli()
            return LocalDay(
                localDate = startDate.toString(),
                startEpochMillis = start,
                endEpochMillis = end,
            )
        }
    }
