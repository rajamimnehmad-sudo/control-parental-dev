package com.contentfilter.dagbrowser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object DagNavigationPolicy {
    private const val GoogleSearchBase = "https://www.google.com/search?safe=active&q="

    fun fromUserInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val candidate =
            when {
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("http://", ignoreCase = true) ->
                    "https://${trimmed.substringAfter("://")}"
                looksLikeHost(trimmed) -> "https://$trimmed"
                else -> GoogleSearchBase + encode(trimmed)
            }
        return sanitizeTopLevel(candidate)
    }

    fun sanitizeTopLevel(url: String): String? {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (parsed.host.isNullOrBlank()) return null
        if (!isGoogleSearch(parsed)) return url
        if (hasStrictSafeSearch(parsed.rawQuery)) return url

        val strictQuery =
            parsed.rawQuery
                .orEmpty()
                .split("&")
                .filter(String::isNotBlank)
                .filterNot { it.substringBefore("=").equals("safe", ignoreCase = true) }
                .plus("safe=active")
                .joinToString("&")
        val fragment = parsed.rawFragment?.let { "#$it" }.orEmpty()
        val withoutQuery = url.substringBefore("#").substringBefore("?")
        return "$withoutQuery?$strictQuery$fragment"
    }

    fun decideLoad(
        url: String,
        opensNewWindow: Boolean,
    ): DagLoadDecision {
        if (url == "about:blank" && !opensNewWindow) {
            return DagLoadDecision.Allow
        }
        val safeUrl = sanitizeTopLevel(url) ?: return DagLoadDecision.Block
        return when {
            opensNewWindow || safeUrl != url -> DagLoadDecision.Redirect(safeUrl)
            else -> DagLoadDecision.Allow
        }
    }

    private fun looksLikeHost(value: String): Boolean =
        !value.any(Char::isWhitespace) &&
            value.substringBefore("/").contains(".") &&
            !value.startsWith(".")

    private fun isGoogleSearch(uri: URI): Boolean {
        val host = uri.host.lowercase().removePrefix("www.")
        return (host == "google.com" || host.startsWith("google.")) &&
            uri.path.trimEnd('/').equals("/search", ignoreCase = true)
    }

    private fun hasStrictSafeSearch(rawQuery: String?): Boolean =
        rawQuery
            ?.split("&")
            ?.any {
                val (key, value) =
                    it.split("=", limit = 2).let { parts ->
                        parts.first() to parts.getOrElse(1) { "" }
                    }
                key.equals("safe", ignoreCase = true) && value.equals("active", ignoreCase = true)
            } == true

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

internal sealed interface DagLoadDecision {
    data object Allow : DagLoadDecision

    data object Block : DagLoadDecision

    data class Redirect(
        val url: String,
    ) : DagLoadDecision
}
