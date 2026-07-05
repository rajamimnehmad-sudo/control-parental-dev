package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "policies",
    indices = [
        Index(value = ["active"]),
        Index(value = ["deviceId"]),
        Index(value = ["updatedAtEpochMillis"]),
    ],
)
data class PolicyEntity(
    @PrimaryKey val id: String,
    val deviceId: String?,
    val version: Long,
    val active: Boolean,
    val updatedAtEpochMillis: Long,
)
