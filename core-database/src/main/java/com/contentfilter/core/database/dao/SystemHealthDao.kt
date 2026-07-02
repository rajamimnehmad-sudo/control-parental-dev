package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.SystemHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemHealthDao {
    @Query("SELECT * FROM system_health WHERE id = :id")
    fun observeCurrent(id: String = SystemHealthEntity.CURRENT_HEALTH_ID): Flow<SystemHealthEntity?>

    @Query("SELECT * FROM system_health WHERE id = :id")
    suspend fun current(id: String = SystemHealthEntity.CURRENT_HEALTH_ID): SystemHealthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SystemHealthEntity)

    @Query("DELETE FROM system_health")
    suspend fun deleteAll()
}
