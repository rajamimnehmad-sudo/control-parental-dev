package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.AccessRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessRequestDao {
    @Query("SELECT * FROM access_requests ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<AccessRequestEntity>>

    @Query("SELECT * FROM access_requests WHERE status IN (:statuses) ORDER BY createdAtEpochMillis DESC")
    fun observeByStatuses(statuses: List<String>): Flow<List<AccessRequestEntity>>

    @Query("SELECT * FROM access_requests WHERE id = :id LIMIT 1")
    suspend fun requestById(id: String): AccessRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: AccessRequestEntity)

    @Query("DELETE FROM access_requests")
    suspend fun deleteAll()

    @Query("DELETE FROM access_requests WHERE id = :id")
    suspend fun deleteById(id: String)
}
