package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName ORDER BY startedAtEpochMillis DESC")
    fun observeForPackage(packageName: String): Flow<List<UsageSessionEntity>>

    @Query(
        """
        SELECT packageName,
            SUM(
                MAX(0, MIN(endedAtEpochMillis, :dayEndEpochMillis) - MAX(startedAtEpochMillis, :dayStartEpochMillis))
            ) AS usedMillis
        FROM usage_sessions
        WHERE deviceId = :deviceId
            AND endedAtEpochMillis IS NOT NULL
            AND startedAtEpochMillis < :dayEndEpochMillis
            AND endedAtEpochMillis > :dayStartEpochMillis
        GROUP BY packageName
        """,
    )
    fun observeDailyUsage(
        deviceId: String,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): Flow<List<DailyUsageProjection>>

    @Query(
        """
        SELECT :packageName AS packageName,
            COALESCE(
                SUM(
                    MAX(0, MIN(endedAtEpochMillis, :dayEndEpochMillis) - MAX(startedAtEpochMillis, :dayStartEpochMillis))
                ),
                0
            ) AS usedMillis
        FROM usage_sessions
        WHERE deviceId = :deviceId
            AND packageName = :packageName
            AND endedAtEpochMillis IS NOT NULL
            AND startedAtEpochMillis < :dayEndEpochMillis
            AND endedAtEpochMillis > :dayStartEpochMillis
        """,
    )
    suspend fun dailyUsageForPackage(
        deviceId: String,
        packageName: String,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): DailyUsageProjection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: UsageSessionEntity)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}
