package com.contentfilter.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Network state contract for sync and update modules.
 */
interface NetworkConnectivity {
    fun observeIsOnline(): Flow<Boolean>
}
