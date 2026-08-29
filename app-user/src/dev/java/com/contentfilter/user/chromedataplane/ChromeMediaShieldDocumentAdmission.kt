package com.contentfilter.user.chromedataplane

import java.util.Locale

internal enum class ChromeMediaShieldDocumentKind {
    TopLevel,
    Subdocument,
}

internal sealed interface ChromeMediaShieldDocumentDisposition {
    data class Transform(
        val kind: ChromeMediaShieldDocumentKind,
        val charset: String,
    ) : ChromeMediaShieldDocumentDisposition

    data class FailClosed(
        val kind: ChromeMediaShieldDocumentKind,
        val reason: String,
    ) : ChromeMediaShieldDocumentDisposition

    data object NotDocument : ChromeMediaShieldDocumentDisposition
}

internal class ChromeMediaShieldDocumentAdmission {
    fun governs(request: ChromePhotosProxyRequest): Boolean =
        request.documentKindOrNull() != null || request.hasConservativeTopLevelFallback()

    fun requiresBufferedDecision(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): Boolean =
        governs(request) ||
            (
                request.hasNoFetchMetadata() &&
                    request.method in SupportedNavigationMethods &&
                    response.hasPotentialDocumentContentType()
            )

    fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest {
        if (!governs(request)) return request
        val headers =
            request.headers.filterNot { header ->
                header.name.lowercase(Locale.US) in RemovedDocumentRequestHeaders
            } + ChromeHttpHeader("Accept-Encoding", "identity")
        return request.copy(headers = headers)
    }

    fun disposition(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromeMediaShieldDocumentDisposition {
        val kind =
            request.documentKindOrNull()
                ?: if (request.hasConservativeTopLevelFallback()) {
                    ChromeMediaShieldDocumentKind.TopLevel
                } else if (
                    request.hasNoFetchMetadata() &&
                    request.method in SupportedNavigationMethods &&
                    response.hasPotentialDocumentContentType()
                ) {
                    return ChromeMediaShieldDocumentDisposition.FailClosed(
                        ChromeMediaShieldDocumentKind.TopLevel,
                        "document_intent_ambiguous_without_fetch_metadata",
                    )
                } else {
                    return ChromeMediaShieldDocumentDisposition.NotDocument
                }
        if (request.method == ChromePhotosProxyRequest.Head) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_head_not_authoritative")
        }
        if (request.method !in SupportedNavigationMethods) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_method_unsupported")
        }
        if (response.statusCode in RedirectCodes) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_redirect_requires_proxy_path")
        }
        if (response.statusCode != 200) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_status_${response.statusCode}")
        }
        if (!response.headers.hasIdentityContentEncoding()) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_encoding_unsupported")
        }
        val contentTypes = response.headers.filter { it.name.equals("Content-Type", true) }.map { it.value }
        if (contentTypes.size != 1) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_content_type_ambiguous")
        }
        val contentType = contentTypes.single()
        val mime = contentType.substringBefore(';').trim().lowercase(Locale.US)
        if (mime != HtmlMimeType) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_mime_unsupported")
        }
        val charset =
            contentType.split(';').drop(1)
                .map(String::trim)
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.trim('"', '\'')
                ?.lowercase(Locale.US)
                ?: Utf8
        if (charset !in AsciiCompatibleCharsets) {
            return ChromeMediaShieldDocumentDisposition.FailClosed(kind, "document_charset_unsupported")
        }
        return ChromeMediaShieldDocumentDisposition.Transform(kind, charset)
    }

    private fun ChromePhotosProxyRequest.documentKindOrNull(): ChromeMediaShieldDocumentKind? {
        val destinations = headerValues("Sec-Fetch-Dest").map { it.trim().lowercase(Locale.US) }.distinct()
        return when {
            destinations == listOf("document") -> ChromeMediaShieldDocumentKind.TopLevel
            destinations.singleOrNull() in SubdocumentDestinations -> ChromeMediaShieldDocumentKind.Subdocument
            destinations.isEmpty() && headerValues("Sec-Fetch-Mode").any { it.equals("navigate", true) } ->
                ChromeMediaShieldDocumentKind.TopLevel
            else -> null
        }
    }

    private fun ChromePhotosProxyRequest.hasConservativeTopLevelFallback(): Boolean =
        hasNoFetchMetadata() &&
            method in SupportedNavigationMethods &&
            headerValues("Upgrade-Insecure-Requests").singleOrNull()?.trim() == "1" &&
            headerValues("Accept").any { value ->
                value.split(',').any { mediaRange ->
                    mediaRange.substringBefore(';').trim().equals(HtmlMimeType, ignoreCase = true)
                }
            }

    private fun ChromePhotosProxyRequest.hasNoFetchMetadata(): Boolean =
        headerValues("Sec-Fetch-Dest").isEmpty() && headerValues("Sec-Fetch-Mode").isEmpty()

    private fun ChromePhotosUpstreamResponse.hasPotentialDocumentContentType(): Boolean {
        val contentTypes = headers.filter { it.name.equals("Content-Type", ignoreCase = true) }.map { it.value }
        if (contentTypes.size != 1) return true
        return contentTypes.single().substringBefore(';').trim().lowercase(Locale.US) in DocumentMimeTypes
    }

    private companion object {
        const val HtmlMimeType = "text/html"
        const val XhtmlMimeType = "application/xhtml+xml"
        const val Get = "GET"
        const val Post = "POST"
        const val Utf8 = "utf-8"
        val AsciiCompatibleCharsets = setOf(Utf8, "us-ascii", "iso-8859-1", "windows-1252")
        val DocumentMimeTypes = setOf(HtmlMimeType, XhtmlMimeType)
        val RemovedDocumentRequestHeaders =
            setOf("accept-encoding", "range", "if-range", "if-none-match", "if-modified-since")
        val RedirectCodes = setOf(301, 302, 303, 307, 308)
        val SupportedNavigationMethods = setOf(Get, Post)
        val SubdocumentDestinations = setOf("iframe", "frame", "fencedframe")
    }
}
