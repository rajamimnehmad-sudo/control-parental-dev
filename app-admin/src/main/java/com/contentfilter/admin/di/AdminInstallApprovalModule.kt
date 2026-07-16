package com.contentfilter.admin.di

import com.contentfilter.core.domain.repository.InstallApprovalStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdminInstallApprovalModule {
    @Provides
    @Singleton
    fun provideInstallApprovalStore(): InstallApprovalStore = NoOpInstallApprovalStore
}

private object NoOpInstallApprovalStore : InstallApprovalStore {
    override fun initializeBaseline(packageNames: Set<String>) = Unit

    override fun isKnown(packageName: String): Boolean = true

    override fun isPending(packageName: String): Boolean = false

    override fun markPending(packageName: String) = Unit

    override fun markApproved(packageName: String) = Unit

    override fun remove(packageName: String) = Unit
}
