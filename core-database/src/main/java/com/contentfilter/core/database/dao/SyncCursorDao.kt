package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.SyncCursorEntity

@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursors WHERE tableName = :tableName")
    suspend fun cursorFor(tableName: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors")
    suspend fun deleteAll()
}
