package com.contentfilter.core.telemetry

import android.content.Context
import com.contentfilter.core.database.dao.TechnicalDiagnosticDao
import com.contentfilter.core.database.entity.TechnicalDiagnosticEntity
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.TelemetryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTelemetryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: TechnicalDiagnosticDao,
    ) : TelemetryRepository {
        override fun observeDiagnostics(): Flow<List<TechnicalDiagnostic>> =
            if (context.isDevPackage()) {
                dao.observeLatest(MaxDiagnostics).map { diagnostics -> diagnostics.map { it.toDomain() } }
            } else {
                flowOf(emptyList())
            }

        override suspend fun record(diagnostic: TechnicalDiagnostic) {
            if (!context.isDevPackage()) return
            dao.insert(diagnostic.toEntity())
            dao.trimToLatest(MaxDiagnostics)
        }

        override suspend fun clearDiagnostics() {
            if (!context.isDevPackage()) return
            dao.deleteAll()
        }

        private fun TechnicalDiagnosticEntity.toDomain(): TechnicalDiagnostic =
            TechnicalDiagnostic(
                id = id,
                type = type,
                message = message,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )

        private fun TechnicalDiagnostic.toEntity(): TechnicalDiagnosticEntity =
            TechnicalDiagnosticEntity(
                id = id,
                type = type,
                message = message,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )

        private fun Context.isDevPackage(): Boolean = packageName.endsWith(".dev")

        private companion object {
            const val MaxDiagnostics = 200
        }
    }
