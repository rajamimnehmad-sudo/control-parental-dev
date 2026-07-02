package com.contentfilter.core.network.config

/**
 * Provides the current authenticated user token when Supabase Auth is available.
 */
interface AuthTokenProvider {
    suspend fun currentToken(): String?
}
