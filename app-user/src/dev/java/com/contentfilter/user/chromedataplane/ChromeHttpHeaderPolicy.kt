package com.contentfilter.user.chromedataplane

import java.util.Locale

internal object ChromeHttpHeaderPolicy {
    private val fixedHopByHop =
        setOf(
            "connection",
            "proxy-connection",
            "keep-alive",
            "transfer-encoding",
            "te",
            "trailer",
            "upgrade",
        )

    private val requestManaged = fixedHopByHop + setOf("host", "content-length", "proxy-authorization")
    private val responseManaged = fixedHopByHop + setOf("content-length")

    fun upstreamRequestHeaders(request: ChromePhotosProxyRequest): List<ChromeHttpHeader> {
        val connectionNamed = connectionNamedHeaders(request.headers)
        return request.headers.filterNot { header ->
            header.name.lowercase(Locale.US) in requestManaged ||
                header.name.lowercase(Locale.US) in connectionNamed ||
                header.value.contains('\r') ||
                header.value.contains('\n')
        }
    }

    fun downstreamResponseHeaders(headers: List<ChromeHttpHeader>): List<ChromeHttpHeader> {
        val connectionNamed = connectionNamedHeaders(headers)
        return headers.filterNot { header ->
            header.name.lowercase(Locale.US) in responseManaged ||
                header.name.lowercase(Locale.US) in connectionNamed ||
                header.value.contains('\r') ||
                header.value.contains('\n')
        }
    }

    fun transformedImageHeaders(
        headers: List<ChromeHttpHeader>,
        contentType: String,
    ): List<ChromeHttpHeader> =
        downstreamResponseHeaders(headers).filterNot { header ->
            header.name.lowercase(Locale.US) in invalidatedImageEntityHeaders
        } +
            listOf(
                ChromeHttpHeader("Content-Type", contentType),
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )

    val invalidatedImageEntityHeaders: Set<String> =
        setOf(
            "content-type",
            "content-encoding",
            "etag",
            "last-modified",
            "content-md5",
            "digest",
            "content-range",
            "accept-ranges",
            "vary",
            "cache-control",
            "expires",
        )

    private fun connectionNamedHeaders(headers: List<ChromeHttpHeader>): Set<String> =
        headers
            .filter { it.name.equals("Connection", ignoreCase = true) }
            .flatMap { it.value.split(',') }
            .mapTo(linkedSetOf()) { it.trim().lowercase(Locale.US) }
}
