package com.contentfilter.core.data

import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.AccountRepository
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.InstalledAppRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAccountRepository(repository: RoomAccountRepository): AccountRepository

    @Binds
    abstract fun bindPolicyRepository(repository: RoomPolicyRepository): PolicyRepository

    @Binds
    abstract fun bindSystemStatusRepository(repository: RoomSystemStatusRepository): SystemStatusRepository

    @Binds
    abstract fun bindDailyLimitRepository(repository: RoomDailyLimitRepository): DailyLimitRepository

    @Binds
    abstract fun bindUsageSessionRepository(repository: RoomUsageSessionRepository): UsageSessionRepository

    @Binds
    abstract fun bindAccessRequestRepository(repository: RoomAccessRequestRepository): AccessRequestRepository

    @Binds
    abstract fun bindExtraTimeGrantRepository(repository: RoomExtraTimeGrantRepository): ExtraTimeGrantRepository

    @Binds
    abstract fun bindDeviceActivationRepository(repository: RoomDeviceActivationRepository): DeviceActivationRepository

    @Binds
    abstract fun bindDeviceRepository(repository: RoomDeviceRepository): DeviceRepository

    @Binds
    abstract fun bindAppGroupRepository(repository: RoomAppGroupRepository): AppGroupRepository

    @Binds
    abstract fun bindInstalledAppRepository(repository: RoomInstalledAppRepository): InstalledAppRepository
}
