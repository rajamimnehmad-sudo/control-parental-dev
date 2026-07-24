package com.contentfilter.user.dag2

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DagV2PolicyModule {
    @Binds
    abstract fun bindSearchPolicy(implementation: DagV2SitePolicy): DagV2SearchPolicy
}
