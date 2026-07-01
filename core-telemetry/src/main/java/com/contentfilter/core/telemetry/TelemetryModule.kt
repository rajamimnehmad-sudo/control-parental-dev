package com.contentfilter.core.telemetry

import com.contentfilter.core.domain.repository.TelemetryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    abstract fun bindTelemetryRepository(repository: InMemoryTelemetryRepository): TelemetryRepository
}
