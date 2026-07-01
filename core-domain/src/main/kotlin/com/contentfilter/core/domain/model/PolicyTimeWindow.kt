package com.contentfilter.core.domain.model

/**
 * Simple daily time window represented as minutes since midnight.
 */
data class PolicyTimeWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinuteOfDay <= endMinuteOfDay) {
            minuteOfDay in startMinuteOfDay..endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay <= endMinuteOfDay
        }
}
