package com.contentfilter.user.dag2

import java.net.URI
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DagV2DocumentRequestContext(
    val sessionId: String,
    val navigationToken: String,
    val documentUrl: String,
    val documentOrigin: String,
    val createdAt: Long,
)

enum class DagV2RequestAttribution {
    Current,
    Stale,
    Unattributed,
}

data class DagV2ResourceEvidence(
    val url: String,
    val headers: Map<String, String>,
    val isForMainFrame: Boolean,
    val source: DagV2ResourceSource,
)

data class DagV2AttributedResource(
    val evidence: DagV2ResourceEvidence,
    val context: DagV2DocumentRequestContext?,
    val attribution: DagV2RequestAttribution,
)

/**
 * Keeps immutable navigation generations and resolves resource ownership from
 * request evidence. It never assigns the current generation merely because it
 * happens to be active.
 */
@Singleton
class DagV2DocumentContextRegistry
    @Inject
    constructor() {
        private val records = LinkedHashMap<String, ContextRecord>()
        private var activeToken: String? = null

        @Synchronized
        fun register(context: DagV2DocumentRequestContext) {
            records[context.navigationToken] =
                ContextRecord(
                    context = context,
                    documentAliases = linkedSetOf(context.documentUrl.normalizedDocumentUrl()),
                    cancelled = false,
                )
            activeToken = context.navigationToken
        }

        @Synchronized
        fun cancel(context: DagV2DocumentRequestContext) {
            records[context.navigationToken]?.cancelled = true
            if (activeToken == context.navigationToken) activeToken = null
        }

        @Synchronized
        fun registerSpaLocation(
            context: DagV2DocumentRequestContext,
            url: String,
        ): Boolean {
            val record = records[context.navigationToken] ?: return false
            if (record.cancelled || activeToken != context.navigationToken) return false
            if (url.dagV2Origin() != context.documentOrigin) return false
            record.documentAliases += url.normalizedDocumentUrl()
            return true
        }

        @Synchronized
        fun resolve(evidence: DagV2ResourceEvidence): DagV2AttributedResource {
            val declaredToken = evidence.headers.header(DagV2NavigationTokenHeader)
            if (declaredToken.isNotBlank()) {
                return records[declaredToken]?.attributed(evidence) ?: evidence.unattributed()
            }
            val referrer = evidence.headers.header("Referer").normalizedDocumentUrlOrNull()
            val origin = evidence.headers.header("Origin").normalizedOriginOrNull()
            val requestDocument = evidence.url.normalizedDocumentUrl()

            val exactCandidates =
                when {
                    evidence.isForMainFrame ->
                        records.values.filter { requestDocument in it.documentAliases }
                    referrer != null ->
                        records.values.filter { referrer in it.documentAliases }
                    else -> emptyList()
                }
            val exact = exactCandidates.singleOrNull()
            if (exact != null) return exact.attributed(evidence)
            if (exactCandidates.size > 1) return evidence.unattributed()

            val originCandidates =
                origin?.let { value ->
                    records.values.filter { it.context.documentOrigin == value }
                }.orEmpty()
            return originCandidates.singleOrNull()?.attributed(evidence) ?: evidence.unattributed()
        }

        /**
         * A WebView created for one navigation owns that immutable generation.
         * This path does not infer ownership from whichever session is active.
         */
        @Synchronized
        fun resolveBound(
            context: DagV2DocumentRequestContext,
            evidence: DagV2ResourceEvidence,
        ): DagV2AttributedResource {
            val record = records[context.navigationToken] ?: return evidence.unattributed()
            if (record.context != context) return evidence.unattributed()
            return record.attributed(evidence)
        }

        @Synchronized
        fun isCurrent(context: DagV2DocumentRequestContext): Boolean {
            val record = records[context.navigationToken] ?: return false
            return !record.cancelled &&
                activeToken == context.navigationToken &&
                record.context.sessionId == context.sessionId
        }

        @Synchronized
        fun context(
            sessionId: String,
            navigationToken: String,
        ): DagV2DocumentRequestContext? =
            records[navigationToken]
                ?.takeIf { it.context.sessionId == sessionId }
                ?.context

        @Synchronized
        fun hasActiveContext(): Boolean =
            activeToken
                ?.let(records::get)
                ?.let { !it.cancelled }
                ?: false

        @Synchronized
        fun clear() {
            records.clear()
            activeToken = null
        }

        private fun ContextRecord.attributed(evidence: DagV2ResourceEvidence): DagV2AttributedResource =
            DagV2AttributedResource(
                evidence = evidence,
                context = context,
                attribution =
                    if (!cancelled && activeToken == context.navigationToken) {
                        DagV2RequestAttribution.Current
                    } else {
                        DagV2RequestAttribution.Stale
                    },
            )

        private data class ContextRecord(
            val context: DagV2DocumentRequestContext,
            val documentAliases: MutableSet<String>,
            var cancelled: Boolean,
        )
    }

@Singleton
class DagV2DocumentCallbackGate
    @Inject
    constructor(
        private val contexts: DagV2DocumentContextRegistry,
        private val sessions: DagV2DocumentSession,
    ) {
        fun register(context: DagV2DocumentRequestContext) {
            contexts.register(context)
        }

        fun cancel(context: DagV2DocumentRequestContext) {
            contexts.cancel(context)
        }

        fun registerSpaLocation(
            context: DagV2DocumentRequestContext,
            url: String,
        ): Boolean = contexts.registerSpaLocation(context, url)

        fun context(
            sessionId: String,
            navigationToken: String,
        ): DagV2DocumentRequestContext? = contexts.context(sessionId, navigationToken)

        fun clear() {
            contexts.clear()
        }

        fun accepts(context: DagV2DocumentRequestContext): Boolean =
            contexts.isCurrent(context) &&
                sessions.isCurrent(context.sessionId, context.navigationToken)

        fun authorizeBridgeMessage(
            sessionId: String,
            navigationToken: String,
            sourceOrigin: String,
            isMainFrame: Boolean,
        ): DagV2DocumentRequestContext? {
            if (!isMainFrame) return null
            return contexts
                .context(sessionId, navigationToken)
                ?.takeIf(::accepts)
                ?.takeIf { sourceOrigin.dagV2Origin() == it.documentOrigin }
        }
    }

internal fun String.dagV2Origin(): String =
    runCatching {
        val uri = URI(this)
        val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
    }.getOrDefault("")

private fun String.normalizedDocumentUrl(): String =
    runCatching {
        val uri = URI(this)
        URI(uri.scheme.lowercase(), uri.authority.lowercase(), uri.path, uri.query, null).toString()
    }.getOrDefault("")

private fun String.normalizedDocumentUrlOrNull(): String? =
    takeIf(String::isNotBlank)
        ?.normalizedDocumentUrl()
        ?.takeIf(String::isNotBlank)

private fun String.normalizedOriginOrNull(): String? =
    takeIf(String::isNotBlank)
        ?.dagV2Origin()
        ?.takeIf(String::isNotBlank)

private fun Map<String, String>.header(name: String): String =
    entries.firstOrNull { it.key.equals(name, true) }?.value.orEmpty()

private fun DagV2ResourceEvidence.unattributed(): DagV2AttributedResource =
    DagV2AttributedResource(this, null, DagV2RequestAttribution.Unattributed)

internal const val DagV2NavigationTokenHeader = "X-DAG-V2-Navigation-Token"
