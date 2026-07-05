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

    @Query(
        """
        SELECT daily_limits.* FROM daily_limits
        INNER JOIN policies ON policies.id = daily_limits.policyId
        WHERE daily_limits.enabled = 1
            AND policies.active = 1
            AND policies.deviceId = :deviceId
        """,
    )
    fun observeEnabledForDevice(deviceId: String): Flow<List<DailyLimitEntity>>

    @Query("SELECT * FROM daily_limits WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): DailyLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: DailyLimitEntity)

    @Query("DELETE FROM daily_limits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM daily_limits")
    suspend fun deleteAll()
}
