package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val tableName: String,
    val updatedAfterIso: String?,
    val syncedAtEpochMillis: Long,
)
