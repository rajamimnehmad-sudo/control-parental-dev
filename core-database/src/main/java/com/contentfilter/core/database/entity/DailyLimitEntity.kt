package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_limits",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["policyId"]),
        Index(value = ["policyId", "targetType", "target"], unique = true),
    ],
)
data class DailyLimitEntity(
    @PrimaryKey val id: String,
    val policyId: String?,
    val targetType: String,
    val target: String,
    val limitMinutes: Int,
    val enabled: Boolean,
    val updatedAtEpochMillis: Long = 0L,
)
