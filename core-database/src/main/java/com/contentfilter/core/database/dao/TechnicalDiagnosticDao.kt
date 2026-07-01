package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.TechnicalDiagnosticEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TechnicalDiagnosticDao {
    @Query("SELECT * FROM technical_diagnostics ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int = 100): Flow<List<TechnicalDiagnosticEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TechnicalDiagnosticEntity)

    @Query("DELETE FROM technical_diagnostics")
    suspend fun deleteAll()
}
