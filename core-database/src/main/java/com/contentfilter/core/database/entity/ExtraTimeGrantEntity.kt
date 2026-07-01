package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extra_time_grants",
    indices = [
        Index(value = ["validUntilEpochMillis"]),
        Index(value = ["targetType", "target"]),
    ],
)
data class ExtraTimeGrantEntity(
    @PrimaryKey val id: String,
    val requestId: String?,
    val targetType: String,
    val target: String,
    val grantedMinutes: Int,
    val validUntilEpochMillis: Long,
)
