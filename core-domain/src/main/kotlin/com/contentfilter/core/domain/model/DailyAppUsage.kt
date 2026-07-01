package com.contentfilter.core.domain.model

/**
 * Aggregated app usage for a local calendar day.
 */
data class DailyAppUsage(
    val packageName: String,
    val deviceId: String,
    val localDate: String,
    val usedMinutes: Int,
)
