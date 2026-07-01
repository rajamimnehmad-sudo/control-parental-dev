package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.TechnicalDiagnostic
import kotlinx.coroutines.flow.Flow

/**
 * Technical diagnostics only. It must never become marketing analytics.
 */
interface TelemetryRepository {
    fun observeDiagnostics(): Flow<List<TechnicalDiagnostic>>

    suspend fun record(diagnostic: TechnicalDiagnostic)
}
