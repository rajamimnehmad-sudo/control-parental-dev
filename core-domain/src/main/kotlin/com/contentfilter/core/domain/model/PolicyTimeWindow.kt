package com.contentfilter.core.domain.model

/**
 * Simple daily time window represented as minutes since midnight.
 */
data class PolicyTimeWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    init {
        require(startMinuteOfDay in 0..1_439) { "Start minute must be between 0 and 1439." }
        require(endMinuteOfDay in 0..1_439) { "End minute must be between 0 and 1439." }
    }

    fun contains(minuteOfDay: Int): Boolean =
        when {
            startMinuteOfDay == endMinuteOfDay -> true
            startMinuteOfDay < endMinuteOfDay -> minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
            else -> minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }

    fun contains(
        time: TimePolicyContext,
        activeDaysMask: Int,
    ): Boolean {
        val applicableDay =
            if (startMinuteOfDay > endMinuteOfDay && time.minuteOfDay < endMinuteOfDay) {
                PolicyWeekdays.previous(time.isoDayOfWeek)
            } else {
                time.isoDayOfWeek
            }
        return PolicyWeekdays.includes(activeDaysMask, applicableDay) && contains(time.minuteOfDay)
    }
}

object PolicyWeekdays {
    const val All: Int = 0b1111111

    fun includes(
        mask: Int,
        isoDayOfWeek: Int,
    ): Boolean = mask and bit(isoDayOfWeek) != 0

    fun bit(isoDayOfWeek: Int): Int {
        require(isoDayOfWeek in 1..7) { "ISO day of week must be between 1 and 7." }
        return 1 shl (isoDayOfWeek - 1)
    }

    fun previous(isoDayOfWeek: Int): Int = if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
}
