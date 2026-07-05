package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "access_requests",
    indices = [
        Index(value = ["status", "createdAtEpochMillis"]),
        Index(value = ["targetType", "target"]),
        Index(value = ["deviceId"]),
    ],
)
data class AccessRequestEntity(
    @PrimaryKey val id: String,
    val deviceId: String?,
    val requestType: String,
    val targetType: String,
    val target: String,
    val targetPackageName: String?,
    val targetDomain: String?,
    val reason: String,
    val requestedMinutes: Int?,
    val status: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
)
