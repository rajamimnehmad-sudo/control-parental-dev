package com.contentfilter.feature.usage

import com.contentfilter.core.domain.time.GloshTime
import java.time.LocalDate

data class UsageDay(
    val localDate: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    companion object {
        fun today(): UsageDay {
            val zone = GloshTime.ArgentinaZone
            val today = LocalDate.now(zone)
            return UsageDay(
                localDate = today.toString(),
                startEpochMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                endEpochMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }
}
