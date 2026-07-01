package com.contentfilter.core.network.config

/**
 * Provides runtime Supabase configuration without hardcoded secrets.
 */
interface SupabaseConfigProvider {
    fun current(): SupabaseConfig
}
