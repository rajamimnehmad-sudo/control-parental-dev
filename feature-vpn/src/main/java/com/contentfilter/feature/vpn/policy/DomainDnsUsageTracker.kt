package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.time.GloshTime
import java.time.Instant

class DomainDnsUsageTracker {
    private val minutesByTarget = mutableMapOf<String, MutableSet<Int>>()
    private var localDate: String? = null

    fun recordMinute(
        target: String,
        epochMillis: Long,
        minuteOfDay: Int,
    ): Int {
        val date =
            Instant.ofEpochMilli(epochMillis)
                .atZone(GloshTime.ArgentinaZone)
                .toLocalDate()
                .toString()
        if (localDate != date) {
            minutesByTarget.clear()
            localDate = date
        }
        val minutes = minutesByTarget.getOrPut(target) { mutableSetOf() }
        minutes += minuteOfDay
        return minutes.size
    }
}
