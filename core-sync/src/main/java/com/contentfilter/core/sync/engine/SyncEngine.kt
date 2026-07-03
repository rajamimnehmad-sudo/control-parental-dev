package com.contentfilter.core.sync.engine

interface SyncEngine {
    suspend fun syncOnce(): SyncResult

    suspend fun syncCoreDataFull(): SyncResult

    suspend fun syncDevicesFull(): SyncResult

    suspend fun syncAccessRequestsFull(): SyncResult

    suspend fun syncRequestResultsFull(): SyncResult
}
