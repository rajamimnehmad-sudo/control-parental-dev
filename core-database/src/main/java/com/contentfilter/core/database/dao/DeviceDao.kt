package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY displayName ASC")
    fun observeDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("DELETE FROM devices WHERE id != :keepDeviceId")
    suspend fun deleteExcept(keepDeviceId: String)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: String)
}
