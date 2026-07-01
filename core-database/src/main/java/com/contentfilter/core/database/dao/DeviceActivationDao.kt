package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.DeviceActivationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceActivationDao {
    @Query("SELECT * FROM device_activations ORDER BY activatedAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<DeviceActivationEntity?>

    @Query("SELECT * FROM device_activations ORDER BY activatedAtEpochMillis DESC LIMIT 1")
    suspend fun latest(): DeviceActivationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activation: DeviceActivationEntity)

    @Query("DELETE FROM device_activations")
    suspend fun deleteAll()

    @Query("DELETE FROM device_activations WHERE deviceId != :keepDeviceId")
    suspend fun deleteExceptDevice(keepDeviceId: String)
}
