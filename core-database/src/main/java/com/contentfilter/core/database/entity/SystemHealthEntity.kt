package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_health")
data class SystemHealthEntity(
    @PrimaryKey val id: String = CURRENT_HEALTH_ID,
    val vpnState: String,
    val accessibilityState: String,
    val deviceAdminState: String = "Unknown",
    val syncState: String,
    val integrityState: String,
    val databaseState: String,
    val licenseState: String,
    val licenseStartsAtEpochMillis: Long? = null,
    val licenseExpiresAtEpochMillis: Long? = null,
    val licenseVerifiedAtEpochMillis: Long? = null,
    val updateState: String,
    val checkedAtEpochMillis: Long,
) {
    companion object {
        const val CURRENT_HEALTH_ID = "current"
    }
}
