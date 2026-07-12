package com.contentfilter.feature.vpn.di

import com.contentfilter.core.policy.DefaultPolicyEngine
import com.contentfilter.core.policy.PolicyEngine
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import com.contentfilter.feature.vpn.domainlist.WebDomainListStore
import com.contentfilter.feature.vpn.policy.SystemVpnClock
import com.contentfilter.feature.vpn.policy.VpnClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {
    @Binds
    abstract fun bindVpnClock(clock: SystemVpnClock): VpnClock

    @Binds
    abstract fun bindDynamicDomainBlocklist(store: WebDomainListStore): DynamicDomainBlocklist

    companion object {
        @Provides
        fun providePolicyEngine(): PolicyEngine = DefaultPolicyEngine()
    }
}
