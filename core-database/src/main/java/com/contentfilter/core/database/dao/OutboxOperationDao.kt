package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.OutboxOperationEntity

@Dao
interface OutboxOperationDao {
    @Query(
        "SELECT * FROM outbox_operations " +
            "WHERE status = :status " +
            "ORDER BY createdAtEpochMillis ASC " +
            "LIMIT :limit",
    )
    suspend fun pending(
        status: String = "Pending",
        limit: Int = 50,
    ): List<OutboxOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: OutboxOperationEntity)

    @Query(
        "UPDATE outbox_operations " +
            "SET status = :status, attemptCount = :attemptCount, updatedAtEpochMillis = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        attemptCount: Int,
        updatedAt: Long,
    )

    @Query("DELETE FROM outbox_operations")
    suspend fun deleteAll()
}
