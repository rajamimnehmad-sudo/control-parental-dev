package com.contentfilter.user.apps

import com.contentfilter.core.domain.repository.InstallApprovalStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InstallApprovalModule {
    @Binds
    @Singleton
    abstract fun bindInstallApprovalStore(store: SharedPreferencesInstallApprovalStore): InstallApprovalStore
}
