package com.contentfilter.feature.vpn.policy

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Clock boundary for policy contexts created by the VPN layer.
 */
interface VpnClock {
    fun nowEpochMillis(): Long

    fun minuteOfDay(epochMillis: Long): Int

    fun isoDayOfWeek(epochMillis: Long): Int
}

class SystemVpnClock
    @Inject
    constructor() : VpnClock {
        override fun nowEpochMillis(): Long = System.currentTimeMillis()

        override fun minuteOfDay(epochMillis: Long): Int {
            val localTime = Instant.ofEpochMilli(epochMillis).atZone(ArgentinaZone).toLocalTime()
            return localTime.hour * MINUTES_PER_HOUR + localTime.minute
        }

        override fun isoDayOfWeek(epochMillis: Long): Int =
            Instant.ofEpochMilli(epochMillis).atZone(ArgentinaZone).dayOfWeek.value

        private companion object {
            const val MINUTES_PER_HOUR = 60
            val ArgentinaZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        }
    }
