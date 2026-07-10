package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_operations",
    indices = [
        Index(value = ["status", "createdAtEpochMillis"]),
        Index(value = ["status", "priority", "createdAtEpochMillis"]),
        Index(value = ["aggregateId", "status", "revision"]),
    ],
)
data class OutboxOperationEntity(
    @PrimaryKey val id: String,
    val tableName: String,
    val operation: String,
    val payload: String,
    val status: String,
    val attemptCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val requestId: String? = null,
    val aggregateId: String? = null,
    val deviceId: String? = null,
    val revision: Long? = null,
    val priority: Int = 0,
)
