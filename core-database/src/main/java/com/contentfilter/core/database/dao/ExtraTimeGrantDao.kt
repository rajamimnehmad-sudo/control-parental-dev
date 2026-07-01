package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.ExtraTimeGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtraTimeGrantDao {
    @Query("SELECT * FROM extra_time_grants ORDER BY validUntilEpochMillis DESC")
    fun observeAll(): Flow<List<ExtraTimeGrantEntity>>

    @Query("SELECT * FROM extra_time_grants WHERE validUntilEpochMillis > :nowEpochMillis")
    fun observeActive(nowEpochMillis: Long): Flow<List<ExtraTimeGrantEntity>>

    @Query("SELECT * FROM extra_time_grants WHERE validUntilEpochMillis > :nowEpochMillis")
    suspend fun active(nowEpochMillis: Long): List<ExtraTimeGrantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: ExtraTimeGrantEntity)

    @Query("DELETE FROM extra_time_grants")
    suspend fun deleteAll()

    @Query("DELETE FROM extra_time_grants WHERE id = :id")
    suspend fun deleteById(id: String)
}
