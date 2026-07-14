package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [Index(value = ["accountId"])],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val displayName: String,
    val appRole: String = "user",
    val lastSeenAtEpochMillis: Long? = null,
    val vpnState: String = "Unknown",
    val accessibilityState: String = "Unknown",
    val deviceAdminState: String = "Unknown",
    val protectionAlert: String? = null,
    val protectionUpdatedAtEpochMillis: Long? = null,
    val appliedPolicyId: String? = null,
    val appliedPolicyRevision: Long? = null,
    val policyAppliedAtEpochMillis: Long? = null,
)
