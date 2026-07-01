package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_sessions",
    indices = [
        Index(value = ["deviceId", "packageName", "startedAtEpochMillis"]),
        Index(value = ["deviceId", "startedAtEpochMillis"]),
        Index(value = ["packageName", "startedAtEpochMillis"]),
        Index(value = ["startedAtEpochMillis"]),
    ],
)
data class UsageSessionEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val packageName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
