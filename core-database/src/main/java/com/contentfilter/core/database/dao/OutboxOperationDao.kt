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
            "ORDER BY priority DESC, createdAtEpochMillis ASC " +
            "LIMIT :limit",
    )
    suspend fun pending(
        status: String = "Pending",
        limit: Int = 50,
    ): List<OutboxOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: OutboxOperationEntity)

    @Query("SELECT * FROM outbox_operations WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): OutboxOperationEntity?

    @Query(
        "SELECT * FROM outbox_operations " +
            "WHERE aggregateId = :aggregateId AND status = :status " +
            "ORDER BY priority DESC, createdAtEpochMillis ASC",
    )
    suspend fun pendingForAggregate(
        aggregateId: String,
        status: String = "Pending",
    ): List<OutboxOperationEntity>

    @Query(
        "SELECT * FROM outbox_operations " +
            "WHERE tableName IN (:tableNames) AND status = :status " +
            "ORDER BY priority DESC, createdAtEpochMillis ASC",
    )
    suspend fun pendingForTables(
        tableNames: List<String>,
        status: String = "Pending",
    ): List<OutboxOperationEntity>

    @Query(
        "SELECT * FROM outbox_operations " +
            "WHERE aggregateId = :aggregateId AND tableName = 'policies' " +
            "ORDER BY revision DESC, updatedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun latestPolicyOperation(aggregateId: String): OutboxOperationEntity?

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

    @Query(
        "UPDATE outbox_operations " +
            "SET status = :status, attemptCount = :attemptCount, updatedAtEpochMillis = :updatedAt " +
            "WHERE id = :id AND updatedAtEpochMillis = :expectedUpdatedAt",
    )
    suspend fun updateStatusIfCurrent(
        id: String,
        expectedUpdatedAt: Long,
        status: String,
        attemptCount: Int,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM outbox_operations")
    suspend fun deleteAll()
}
