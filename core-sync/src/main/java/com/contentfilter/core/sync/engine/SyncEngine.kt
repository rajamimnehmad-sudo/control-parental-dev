package com.contentfilter.core.sync.engine

interface SyncEngine {
    suspend fun syncOnce(): SyncResult

    suspend fun syncAccessRequestsFull(): SyncResult

    suspend fun syncRequestResultsFull(): SyncResult
}
