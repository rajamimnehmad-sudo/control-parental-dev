package com.contentfilter.feature.accessibility.policy

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

interface AccessibilityClock {
    fun elapsedRealtimeMillis(): Long

    fun nowEpochMillis(): Long

    fun minuteOfDay(epochMillis: Long): Int

    fun isoDayOfWeek(epochMillis: Long): Int
}

class SystemAccessibilityClock
    @Inject
    constructor() : AccessibilityClock {
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

        override fun nowEpochMillis(): Long = System.currentTimeMillis()

        override fun minuteOfDay(epochMillis: Long): Int {
            val localTime = Instant.ofEpochMilli(epochMillis).atZone(ArgentinaZone).toLocalTime()
            return localTime.hour * MinutesPerHour + localTime.minute
        }

        override fun isoDayOfWeek(epochMillis: Long): Int =
            Instant.ofEpochMilli(epochMillis).atZone(ArgentinaZone).dayOfWeek.value

        private companion object {
            const val MinutesPerHour = 60
            val ArgentinaZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        }
    }
