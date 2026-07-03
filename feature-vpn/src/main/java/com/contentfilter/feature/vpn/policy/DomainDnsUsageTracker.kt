package com.contentfilter.feature.vpn.policy

import java.time.Instant
import java.time.ZoneId

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
                .atZone(ZoneId.systemDefault())
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
