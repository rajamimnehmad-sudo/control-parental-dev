package com.contentfilter.feature.accessibility.di

import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.LocalDayProvider
import com.contentfilter.feature.accessibility.policy.SystemAccessibilityClock
import com.contentfilter.feature.accessibility.policy.SystemLocalDayProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityModule {
    @Binds
    abstract fun bindAccessibilityClock(clock: SystemAccessibilityClock): AccessibilityClock

    @Binds
    abstract fun bindLocalDayProvider(provider: SystemLocalDayProvider): LocalDayProvider
}
