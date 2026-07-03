package com.contentfilter.core.network

import com.contentfilter.core.network.config.BuildConfigSupabaseConfigProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.SupabaseRemoteLimitRepository
import com.contentfilter.core.network.remote.SupabaseRemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.SupabaseRemotePolicyRepository
import com.contentfilter.core.network.remote.SupabaseRemoteRequestRepository
import com.contentfilter.core.network.remote.SupabaseRemoteDeviceRepository
import com.contentfilter.core.network.realtime.RealtimeSubscription
import com.contentfilter.core.network.realtime.SupabaseRealtimeSubscription
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindSupabaseConfigProvider(provider: BuildConfigSupabaseConfigProvider): SupabaseConfigProvider

    @Binds
    abstract fun bindRemotePolicyRepository(repository: SupabaseRemotePolicyRepository): RemotePolicyRepository

    @Binds
    abstract fun bindRemoteLimitRepository(repository: SupabaseRemoteLimitRepository): RemoteLimitRepository

    @Binds
    abstract fun bindRemoteDeviceRepository(repository: SupabaseRemoteDeviceRepository): RemoteDeviceRepository

    @Binds
    abstract fun bindRemoteInstalledAppRepository(repository: SupabaseRemoteInstalledAppRepository): RemoteInstalledAppRepository

    @Binds
    abstract fun bindRemoteRequestRepository(repository: SupabaseRemoteRequestRepository): RemoteRequestRepository

    @Binds
    abstract fun bindRealtimeSubscription(subscription: SupabaseRealtimeSubscription): RealtimeSubscription

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
    }
}
