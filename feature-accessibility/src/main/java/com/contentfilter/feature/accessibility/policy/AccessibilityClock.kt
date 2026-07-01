package com.contentfilter.feature.accessibility.policy

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

interface AccessibilityClock {
    fun elapsedRealtimeMillis(): Long

    fun nowEpochMillis(): Long

    fun minuteOfDay(epochMillis: Long): Int
}

class SystemAccessibilityClock
    @Inject
    constructor() : AccessibilityClock {
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

        override fun nowEpochMillis(): Long = System.currentTimeMillis()

        override fun minuteOfDay(epochMillis: Long): Int {
            val localTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
            return localTime.hour * MinutesPerHour + localTime.minute
        }

        private companion object {
            const val MinutesPerHour = 60
        }
    }
