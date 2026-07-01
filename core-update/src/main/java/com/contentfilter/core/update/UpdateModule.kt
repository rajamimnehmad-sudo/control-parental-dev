package com.contentfilter.core.update

import com.contentfilter.core.update.config.BuildConfigUpdateConfigProvider
import com.contentfilter.core.update.config.UpdateConfigProvider
import com.contentfilter.core.update.install.AndroidApkInstaller
import com.contentfilter.core.update.install.ApkInstaller
import com.contentfilter.core.update.repository.ApkUpdateRepository
import com.contentfilter.core.update.repository.DevApkUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    abstract fun bindUpdateConfigProvider(
        provider: BuildConfigUpdateConfigProvider,
    ): UpdateConfigProvider

    @Binds
    abstract fun bindApkUpdateRepository(
        repository: DevApkUpdateRepository,
    ): ApkUpdateRepository

    @Binds
    abstract fun bindApkInstaller(
        installer: AndroidApkInstaller,
    ): ApkInstaller
}
