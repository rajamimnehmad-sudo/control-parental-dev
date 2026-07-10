package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Query("SELECT MAX(updatedAtEpochMillis) FROM installed_apps WHERE deviceId = :deviceId")
    suspend fun latestUpdatedAt(deviceId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<InstalledAppEntity>)

    @Query("DELETE FROM installed_apps WHERE deviceId = :deviceId")
    suspend fun deleteForDevice(deviceId: String)
}
