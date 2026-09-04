package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.util.Locale

internal class ChromeMediaShieldCspPolicy(
    private val sameOriginReady: Boolean = false,
    private val mediaAuthorityEnabled: Boolean = false,
) {
    private val mediaEnvelope: String
        get() =
            "img-src https: http: $OriginalUiSvgOrigin; media-src https: http:" +
                (if (mediaAuthorityEnabled) " blob:" else "") +
                "; object-src 'none'; worker-src https: http:; " +
                "frame-src https: http:; child-src https: http:; fenced-frame-src 'none'"

    fun apply(
        sourceHeaders: List<ChromeHttpHeader>,
        scriptNonce: String,
        styleNonce: String,
        charset: String = "utf-8",
    ): List<ChromeHttpHeader> {
        val sanitized = ChromeHttpHeaderPolicy.downstreamResponseHeaders(sourceHeaders)
        val output = mutableListOf<ChromeHttpHeader>()
        sanitized.forEach { header ->
            when {
                header.name.equals(ContentSecurityPolicyReportOnly, ignoreCase = true) -> Unit
                header.name.equals(ContentSecurityPolicy, ignoreCase = true) -> {
                    splitEffectivePolicies(header.value).forEach { effectivePolicy ->
                        output +=
                            ChromeHttpHeader(
                                ContentSecurityPolicy,
                                admitBootstrap(effectivePolicy, scriptNonce, styleNonce),
                            )
                    }
                }
                header.name.lowercase(Locale.US) in InvalidatedDocumentEntityHeaders -> Unit
                else -> output += header
            }
        }
        output += ChromeHttpHeader(ContentSecurityPolicy, mediaEnvelope)
        output += ChromeHttpHeader("Content-Type", "text/html; charset=$charset")
        output += ChromeHttpHeader("Cache-Control", "no-store")
        return output
    }

    internal fun admitBootstrap(
        policy: String,
        scriptNonce: String,
        styleNonce: String,
    ): String {
        val directives = parse(policy)
        admitNonceFor(directives, "script-src-elem", "script-src", scriptNonce)
        admitNonceFor(directives, "style-src-elem", "style-src", styleNonce)
        admitReadyOrigin(directives)
        admitOriginalUiSvgOrigin(directives)
        return serialize(directives)
    }

    internal fun rewriteMetaPolicy(policy: String): String? {
        if (policy.isBlank() || '&' in policy || '<' in policy || '>' in policy) return null
        val directives = parse(policy)
        if (directives.isEmpty()) return null
        admitReadyOrigin(directives)
        admitOriginalUiSvgOrigin(directives)
        return serialize(directives)
    }

    private fun serialize(directives: LinkedHashMap<String, Directive>): String =
        directives.values.joinToString("; ") { directive ->
            buildString {
                append(directive.name)
                if (directive.sources.isNotEmpty()) append(' ').append(directive.sources.joinToString(" "))
            }
        }

    private fun admitReadyOrigin(directives: LinkedHashMap<String, Directive>) {
        val targetKey = "connect-src"
        val inherited =
            directives[targetKey]?.sources
                ?: directives["default-src"]?.sources
                ?: return
        val sources = inherited.filterNot { it.equals("'none'", ignoreCase = true) }.toMutableList()
        val readySource = if (sameOriginReady) SelfSource else ReadyOrigin
        if (readySource !in sources) sources += readySource
        directives[targetKey] = Directive(directives[targetKey]?.name ?: targetKey, sources)
    }

    private fun admitOriginalUiSvgOrigin(directives: LinkedHashMap<String, Directive>) {
        val inherited = directives["img-src"]?.sources ?: directives["default-src"]?.sources ?: return
        val sources = inherited.filterNot { it.equals("'none'", ignoreCase = true) }.toMutableList()
        if (OriginalUiSvgOrigin !in sources) sources += OriginalUiSvgOrigin
        directives["img-src"] = Directive(directives["img-src"]?.name ?: "img-src", sources)
    }

    internal fun splitEffectivePolicies(value: String): List<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf(EmptyFailClosedPolicy) }

    private fun admitNonceFor(
        directives: LinkedHashMap<String, Directive>,
        elementDirective: String,
        generalDirective: String,
        nonce: String,
    ) {
        val nonceSource = "'nonce-$nonce'"
        val targetKey =
            when {
                directives.containsKey(elementDirective) -> elementDirective
                directives.containsKey(generalDirective) -> generalDirective
                directives.containsKey("default-src") -> generalDirective
                else -> return
            }
        val inherited =
            directives[targetKey]?.sources
                ?: directives["default-src"]?.sources
                ?: emptyList()
        if (inherited.authorizesUnrestrictedInline()) return
        val sources = inherited.filterNot { it.equals("'none'", ignoreCase = true) }.toMutableList()
        if (nonceSource !in sources) sources += nonceSource
        val existingName = directives[targetKey]?.name ?: targetKey
        directives[targetKey] = Directive(existingName, sources)
    }

    private fun List<String>.authorizesUnrestrictedInline(): Boolean =
        any { it.equals("'unsafe-inline'", ignoreCase = true) } &&
            none { source ->
                val normalized = source.lowercase(Locale.US)
                normalized.startsWith("'nonce-") || normalized.startsWith("'sha256-") ||
                    normalized.startsWith("'sha384-") || normalized.startsWith("'sha512-")
            }

    private fun parse(policy: String): LinkedHashMap<String, Directive> {
        val directives = linkedMapOf<String, Directive>()
        policy.split(';').forEach { raw ->
            val tokens = raw.trim().split(Whitespace).filter(String::isNotEmpty)
            if (tokens.isEmpty()) return@forEach
            val key = tokens.first().lowercase(Locale.US)
            if (key !in directives) directives[key] = Directive(tokens.first(), tokens.drop(1))
        }
        return directives
    }

    private data class Directive(
        val name: String,
        val sources: List<String>,
    )

    private companion object {
        const val ContentSecurityPolicy = "Content-Security-Policy"
        const val ContentSecurityPolicyReportOnly = "Content-Security-Policy-Report-Only"
        const val EmptyFailClosedPolicy = "default-src 'none'"
        const val ReadyOrigin = "https://${ChromePhotosDataPlaneLabContract.FixtureHost}"
        const val OriginalUiSvgOrigin = ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin
        const val SelfSource = "'self'"
        val Whitespace = Regex("\\s+")
        val InvalidatedDocumentEntityHeaders =
            setOf(
                "content-type",
                "content-encoding",
                "content-length",
                "content-range",
                "etag",
                "last-modified",
                "content-md5",
                "content-digest",
                "repr-digest",
                "digest",
                "accept-ranges",
                "vary",
                "cache-control",
                "expires",
            )
    }
}
