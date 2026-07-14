package com.contentfilter.core.security

import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.DeviceTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindAuthSessionStore(store: SharedPreferencesAuthSessionStore): AuthSessionStore

    @Binds
    abstract fun bindAuthTokenProvider(provider: StoredAuthTokenProvider): AuthTokenProvider

    @Binds
    abstract fun bindDeviceTokenProvider(provider: SharedPreferencesDeviceTokenProvider): DeviceTokenProvider

    @Binds
    abstract fun bindActivationRepository(repository: DefaultActivationRepository): ActivationRepository

    @Binds
    abstract fun bindProtectionStateStore(store: EncryptedProtectionStateStore): ProtectionStateStore
}
