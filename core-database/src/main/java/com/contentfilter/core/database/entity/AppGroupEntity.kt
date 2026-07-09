package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_groups",
    indices = [
        Index(value = ["deviceId"]),
        Index(value = ["enabled"]),
    ],
)
data class AppGroupEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val name: String,
    val color: String,
    val limitMinutes: Int,
    val resetMinuteOfDay: Int,
    val enabled: Boolean,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "app_group_apps",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["packageName"]),
        Index(value = ["groupId", "packageName"], unique = true),
    ],
)
data class AppGroupAppEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val packageName: String,
    val enabled: Boolean,
    val updatedAtEpochMillis: Long,
)
