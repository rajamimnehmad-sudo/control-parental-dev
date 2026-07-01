package com.contentfilter.feature.accessibility.time

data class UsageTransition(
    val packageName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
)
