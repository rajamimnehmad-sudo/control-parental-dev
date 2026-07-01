package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_health")
data class SystemHealthEntity(
    @PrimaryKey val id: String = CurrentHealthId,
    val vpnState: String,
    val accessibilityState: String,
    val syncState: String,
    val integrityState: String,
    val databaseState: String,
    val licenseState: String,
    val updateState: String,
    val checkedAtEpochMillis: Long,
) {
    companion object {
        const val CurrentHealthId = "current"
    }
}
