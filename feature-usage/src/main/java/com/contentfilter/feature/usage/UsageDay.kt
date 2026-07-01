package com.contentfilter.feature.usage

import java.time.LocalDate
import java.time.ZoneId

data class UsageDay(
    val localDate: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    companion object {
        fun today(): UsageDay {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            return UsageDay(
                localDate = today.toString(),
                startEpochMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                endEpochMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }
}
