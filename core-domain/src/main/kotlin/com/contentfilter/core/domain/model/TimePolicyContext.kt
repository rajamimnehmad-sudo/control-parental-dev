package com.contentfilter.core.domain.model

data class TimePolicyContext(
    val evaluatedAtEpochMillis: Long,
    val minuteOfDay: Int,
    val isoDayOfWeek: Int = 1,
)
