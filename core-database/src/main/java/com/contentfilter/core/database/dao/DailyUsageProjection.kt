package com.contentfilter.core.database.dao

data class DailyUsageProjection(
    val packageName: String,
    val usedMillis: Long,
)
