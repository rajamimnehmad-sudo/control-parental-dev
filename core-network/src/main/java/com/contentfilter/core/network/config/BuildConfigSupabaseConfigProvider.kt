package com.contentfilter.core.network.config

import com.contentfilter.core.network.BuildConfig
import javax.inject.Inject

class BuildConfigSupabaseConfigProvider
    @Inject
    constructor() : SupabaseConfigProvider {
        private val config =
            SupabaseConfig(
                url = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY,
            )

        override fun current(): SupabaseConfig = config
    }
