package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_activations")
data class DeviceActivationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val deviceId: String,
    val activatedAtEpochMillis: Long,
)
