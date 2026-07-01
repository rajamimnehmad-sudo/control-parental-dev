package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Provides local protection health without exposing Android services to domain code.
 */
interface SystemStatusRepository {
    fun observeHealth(): Flow<SystemHealthSnapshot>

    suspend fun currentHealth(): SystemHealthSnapshot

    suspend fun updateVpnState(state: ComponentState)

    suspend fun updateAccessibilityState(state: ComponentState)
}
