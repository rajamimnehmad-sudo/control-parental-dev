package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.DailyLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLimitDao {
    @Query("SELECT * FROM daily_limits WHERE enabled = 1")
    fun observeEnabled(): Flow<List<DailyLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: DailyLimitEntity)

    @Query("DELETE FROM daily_limits")
    suspend fun deleteAll()
}
