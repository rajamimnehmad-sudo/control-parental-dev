package com.contentfilter.core.network.config

import android.util.Log

/**
 * Public Supabase client configuration.
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
) {
    val isConfigured: Boolean
        get() = normalizedUrlOrNull() != null && anonKey.isNotBlank()

    fun normalizedUrlOrNull(): String? {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank() || anonKey.isBlank()) return null
        if (normalized.contains("tu-proyecto", ignoreCase = true)) {
            Log.w(LogTag, "Supabase URL ignored because it still contains a placeholder.")
            return null
        }
        if (!normalized.startsWith("https://")) {
            Log.w(LogTag, "Supabase URL ignored because it is not HTTPS: ${normalized.hostForLog()}")
            return null
        }
        if (!normalized.endsWith(".supabase.co")) {
            Log.w(LogTag, "Supabase URL host is not a Supabase project host: ${normalized.hostForLog()}")
        }
        Log.i(LogTag, "Supabase URL configured for host=${normalized.hostForLog()}")
        return normalized
    }

    private fun String.hostForLog(): String =
        removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')

    private companion object {
        const val LogTag = "SupabaseConfig"
    }
}
