package com.contentfilter.core.sync

/**
 * Schedules offline-first synchronization work.
 */
interface SyncScheduler {
    fun schedulePeriodicSync()

    fun requestSync()
}
