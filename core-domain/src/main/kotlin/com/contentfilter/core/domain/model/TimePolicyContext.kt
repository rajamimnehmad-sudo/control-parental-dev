package com.contentfilter.core.domain.model

data class TimePolicyContext(
    val evaluatedAtEpochMillis: Long,
    val minuteOfDay: Int,
)
