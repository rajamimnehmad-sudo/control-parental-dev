package com.contentfilter.feature.vpn.service

import android.content.Context

object BlockedDomainSignal {
    private const val PrefsName = "blocked_domains"
    private const val DomainsKey = "domains"
    private const val MaxRecentBlockedDomains = 20

    fun remember(
        context: Context,
        domain: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val current =
            context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(DomainsKey, "")
                .orEmpty()
                .split("|")
                .mapNotNull { it.toEntryOrNull() }
                .filter { it.domain != domain }
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(
                DomainsKey,
                (listOf(BlockedDomainEntry(domain, nowEpochMillis)) + current)
                    .take(MaxRecentBlockedDomains)
                    .joinToString("|") { "${it.domain},${it.blockedAtEpochMillis}" },
            )
            .apply()
    }

    fun mostRecent(context: Context): BlockedDomainEntry? =
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getString(DomainsKey, "")
            .orEmpty()
            .split("|")
            .mapNotNull { it.toEntryOrNull() }
            .maxByOrNull { it.blockedAtEpochMillis }

    private fun String.toEntryOrNull(): BlockedDomainEntry? {
        if (isBlank()) return null
        val domain = substringBefore(",").trim().lowercase()
        val timestamp =
            substringAfter(",", missingDelimiterValue = "")
                .toLongOrNull()
                ?: System.currentTimeMillis()
        return domain.takeIf { it.isNotBlank() }?.let { BlockedDomainEntry(it, timestamp) }
    }
}

data class BlockedDomainEntry(
    val domain: String,
    val blockedAtEpochMillis: Long,
)
