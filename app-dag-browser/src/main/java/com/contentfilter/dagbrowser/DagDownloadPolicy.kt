package com.contentfilter.dagbrowser

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class DagDownloadGesture(
    val targetUrl: String,
    val pageUrl: String,
    val tabRevision: Long,
    val createdAtMillis: Long,
)

internal data class DagDownloadCandidate(
    val responseUrl: String,
    val currentPageUrl: String,
    val currentTabRevision: Long,
    val secure: Boolean,
    val redirected: Boolean,
    val statusCode: Int,
    val mimeType: String?,
    val declaredBytes: Long?,
    val suggestedFileName: String?,
    val nowMillis: Long,
)

internal data class DagAllowedDownload(
    val responseUrl: String,
    val host: String,
    val fileName: String,
    val declaredBytes: Long,
)

internal sealed interface DagDownloadDecision {
    data class Allow(val download: DagAllowedDownload) : DagDownloadDecision

    data class Block(val reason: String) : DagDownloadDecision
}

internal object DagDownloadPolicy {
    const val PdfMimeType = "application/pdf"
    const val MaxBytes = 20L * 1024L * 1024L
    const val GestureWindowMillis = 10_000L

    fun recordGesture(
        requestUrl: String,
        triggerUrl: String?,
        currentPageUrl: String,
        tabRevision: Long,
        pageVisible: Boolean,
        hasUserGesture: Boolean,
        opensNewWindow: Boolean,
        nowMillis: Long,
    ): DagDownloadGesture? {
        if (!pageVisible || !hasUserGesture || opensNewWindow) return null
        if (!isHttps(requestUrl) || !isHttps(currentPageUrl)) return null
        if (triggerUrl != null && !sameDocument(triggerUrl, currentPageUrl)) return null
        return DagDownloadGesture(
            targetUrl = requestUrl,
            pageUrl = currentPageUrl,
            tabRevision = tabRevision,
            createdAtMillis = nowMillis,
        )
    }

    fun decide(
        gesture: DagDownloadGesture?,
        candidate: DagDownloadCandidate,
    ): DagDownloadDecision {
        if (gesture == null) return blocked("missing_user_gesture")
        val age = candidate.nowMillis - gesture.createdAtMillis
        if (age !in 0..GestureWindowMillis) return blocked("expired_user_gesture")
        if (
            candidate.currentTabRevision != gesture.tabRevision ||
            !sameDocument(candidate.currentPageUrl, gesture.pageUrl)
        ) {
            return blocked("page_changed")
        }
        if (!candidate.secure || !isHttps(candidate.responseUrl)) return blocked("insecure_response")
        if (candidate.statusCode !in 200..299) return blocked("response_status")
        if (normalizedMime(candidate.mimeType) != PdfMimeType) return blocked("blocked_mime")
        val declaredBytes = candidate.declaredBytes ?: return blocked("unknown_size")
        if (declaredBytes !in 1..MaxBytes) return blocked("blocked_size")
        val fileName =
            safePdfFileName(candidate.suggestedFileName)
                ?: return blocked("blocked_extension")
        val exactTarget = sameDocument(candidate.responseUrl, gesture.targetUrl)
        if (!exactTarget && (!candidate.redirected || !sameOrigin(candidate.responseUrl, gesture.targetUrl))) {
            return blocked("blocked_redirect")
        }
        val host = uri(candidate.responseUrl)?.host?.lowercase(Locale.ROOT) ?: return blocked("invalid_url")
        return DagDownloadDecision.Allow(
            DagAllowedDownload(
                responseUrl = candidate.responseUrl,
                host = host,
                fileName = fileName,
                declaredBytes = declaredBytes,
            ),
        )
    }

    fun header(
        headers: Map<String, String>,
        name: String,
    ): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun declaredLength(headers: Map<String, String>): Long? = header(headers, "Content-Length")?.trim()?.toLongOrNull()

    fun suggestedFileName(
        headers: Map<String, String>,
        responseUrl: String,
    ): String? {
        val disposition = header(headers, "Content-Disposition").orEmpty()
        val encoded =
            Regex("""(?i)(?:^|;)\s*filename\*\s*=\s*UTF-8''([^;]+)""")
                .find(disposition)
                ?.groupValues
                ?.get(1)
                ?.trim()
        if (!encoded.isNullOrBlank()) {
            val decoded =
                runCatching {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        val quoted =
            Regex("""(?i)(?:^|;)\s*filename\s*=\s*"([^"]+)"""")
                .find(disposition)
                ?.groupValues
                ?.get(1)
        if (!quoted.isNullOrBlank()) return quoted
        val plain =
            Regex("""(?i)(?:^|;)\s*filename\s*=\s*([^;]+)""")
                .find(disposition)
                ?.groupValues
                ?.get(1)
                ?.trim()
        if (!plain.isNullOrBlank()) return plain
        return uri(responseUrl)?.path?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    }

    fun safePdfFileName(rawName: String?): String? {
        val leaf = rawName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        if (!leaf.endsWith(".pdf", ignoreCase = true)) return null
        val stem =
            leaf.dropLast(4)
                .replace(Regex("""[^A-Za-z0-9 ._-]"""), "_")
                .trim(' ', '.')
                .take(72)
                .ifBlank { "documento" }
        return "$stem.pdf"
    }

    fun looksLikePdf(
        header: ByteArray,
        tail: ByteArray,
    ): Boolean {
        val pdfHeader = "%PDF-".toByteArray(StandardCharsets.US_ASCII)
        val eof = "%%EOF".toByteArray(StandardCharsets.US_ASCII)
        return header.startsWith(pdfHeader) && tail.containsSequence(eof)
    }

    private fun normalizedMime(mimeType: String?): String? =
        mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)

    private fun sameDocument(
        first: String,
        second: String,
    ): Boolean {
        val firstUri = uri(first) ?: return false
        val secondUri = uri(second) ?: return false
        return withoutFragment(firstUri) == withoutFragment(secondUri)
    }

    private fun sameOrigin(
        first: String,
        second: String,
    ): Boolean {
        val firstUri = uri(first) ?: return false
        val secondUri = uri(second) ?: return false
        return firstUri.scheme.equals(secondUri.scheme, ignoreCase = true) &&
            firstUri.host.equals(secondUri.host, ignoreCase = true) &&
            effectivePort(firstUri) == effectivePort(secondUri)
    }

    private fun effectivePort(uri: URI): Int =
        when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            else -> -1
        }

    private fun isHttps(url: String): Boolean = uri(url)?.scheme.equals("https", ignoreCase = true)

    private fun uri(url: String): URI? =
        runCatching { URI(url) }
            .getOrNull()
            ?.takeIf { it.isAbsolute && !it.host.isNullOrBlank() }

    private fun withoutFragment(uri: URI): URI =
        URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, uri.query, null)

    private fun blocked(reason: String) = DagDownloadDecision.Block(reason)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty() || size < sequence.size) return false
        return (0..size - sequence.size).any { start ->
            sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }
    }
}
