package com.contentfilter.dagbrowser

import java.net.URI
import java.net.URLDecoder
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
        if (isLabFixture(parsed)) return url
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
        val safeUrl =
            sanitizeTopLevel(url)
                ?: return if (isExternalAppLink(url)) {
                    DagLoadDecision.BlockExternalApp(httpsFallbackFromExternalLink(url))
                } else {
                    DagLoadDecision.Block
                }
        return when {
            opensNewWindow || safeUrl != url -> DagLoadDecision.Redirect(safeUrl)
            else -> DagLoadDecision.Allow
        }
    }

    internal fun httpsFallbackFromExternalLink(url: String): String? {
        val fallbackMarker = "S.browser_fallback_url="
        val encodedFallback =
            url
                .substringAfter(fallbackMarker, missingDelimiterValue = "")
                .substringBefore(";")
        if (encodedFallback.isNotBlank()) {
            val decoded =
                runCatching {
                    URLDecoder.decode(encodedFallback, StandardCharsets.UTF_8.toString())
                }.getOrNull()
            sanitizeTopLevel(decoded.orEmpty())?.let { return it }
        }

        if (!url.startsWith("intent://", ignoreCase = true)) return null
        val intentParameters = url.substringAfter("#Intent;", missingDelimiterValue = "")
        val authorityAndPath =
            url
                .substringAfter("intent://")
                .substringBefore("#Intent;")
        if (
            authorityAndPath.isBlank() ||
            !intentParameters
                .split(";")
                .any { it.equals("scheme=https", ignoreCase = true) }
        ) {
            return null
        }
        return sanitizeTopLevel("https://$authorityAndPath")
    }

    private fun isExternalAppLink(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme == "intent" ||
            scheme !in
            setOf(
                "about",
                "blob",
                "content",
                "data",
                "file",
                "http",
                "https",
                "javascript",
            )
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

    private fun isLabFixture(uri: URI): Boolean =
        BuildConfig.GLOSHIA_LAB_FIXTURE &&
            uri.scheme.equals("http", ignoreCase = true) &&
            (uri.host.equals("localhost", ignoreCase = true) || uri.host == "127.0.0.1") &&
            uri.path.startsWith("/fixture/")

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

    data class BlockExternalApp(
        val httpsFallback: String?,
    ) : DagLoadDecision

    data class Redirect(
        val url: String,
    ) : DagLoadDecision
}
