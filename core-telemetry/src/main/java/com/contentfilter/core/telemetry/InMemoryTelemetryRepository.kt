package com.contentfilter.core.telemetry

import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.TelemetryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class InMemoryTelemetryRepository
    @Inject
    constructor() : TelemetryRepository {
        private val diagnostics = MutableStateFlow<List<TechnicalDiagnostic>>(emptyList())

        override fun observeDiagnostics(): Flow<List<TechnicalDiagnostic>> = diagnostics.asStateFlow()

        override suspend fun record(diagnostic: TechnicalDiagnostic) {
            diagnostics.value = (listOf(diagnostic) + diagnostics.value).take(MaxDiagnostics)
        }

        private companion object {
            const val MaxDiagnostics = 100
        }
    }
