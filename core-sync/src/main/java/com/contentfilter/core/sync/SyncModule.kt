package com.contentfilter.core.sync

import com.contentfilter.core.sync.engine.DefaultSyncEngine
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.outbox.DefaultOutboxProcessor
import com.contentfilter.core.sync.outbox.OutboxProcessor
import com.contentfilter.core.sync.realtime.DefaultRealtimeSyncCoordinator
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindSyncEngine(engine: DefaultSyncEngine): SyncEngine

    @Binds
    abstract fun bindOutboxProcessor(processor: DefaultOutboxProcessor): OutboxProcessor

    @Binds
    abstract fun bindRealtimeSyncCoordinator(coordinator: DefaultRealtimeSyncCoordinator): RealtimeSyncCoordinator
}
