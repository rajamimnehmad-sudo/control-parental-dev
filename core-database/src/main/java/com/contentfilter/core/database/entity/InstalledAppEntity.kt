package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installed_apps",
    indices = [
        Index(value = ["deviceId"]),
        Index(value = ["deviceId", "packageName"], unique = true),
        Index(value = ["updatedAtEpochMillis"]),
    ],
)
data class InstalledAppEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val deviceId: String,
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val iconBase64: String?,
    val updatedAtEpochMillis: Long,
)
