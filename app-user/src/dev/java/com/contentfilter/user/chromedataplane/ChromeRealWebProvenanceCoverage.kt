package com.contentfilter.user.chromedataplane

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

internal enum class ChromeVisibleMediaCoverage {
    AuthoritativePreRender,
    DefiniteNonInterceptable,
    AttributionUnknown,
}

internal enum class ChromeNonInterceptableReason {
    DataUrl,
    BlobUrl,
    Canvas,
    WebGl,
    InlineSvg,
    ServiceWorkerResponse,
    CacheStorageResponse,
    BrowserCacheWithoutLedger,
}

internal enum class ChromeCoverageResourceOutcome {
    Inspected,
    FailClosed,
    Redirect,
    Failure,
}

internal data class ChromeCoverageRequestToken(
    val sessionId: String,
    val stateSequence: Long,
    val navigationSequence: Long,
    val requestSequence: Long,
    val correlationId: String,
    val requestUrlHash: String,
    val canonicalHost: String,
    val hostHash: String,
    val originClass: String,
    val refererHash: String?,
    val destination: String,
    val redirectFromCorrelationId: String?,
    val observedSequence: Long,
)

internal data class ChromeCoverageResourceEvent(
    val token: ChromeCoverageRequestToken,
    val outcome: ChromeCoverageResourceOutcome,
    val statusCode: Int,
    val declaredContentType: String,
    val canonicalContentType: String,
    val bodyDigest: String?,
    val byteLength: Int,
    val redirectTargetHash: String?,
    val decision: String,
    val reason: String,
    val verdictSource: String,
    val verdictCacheHit: Boolean,
    val bodyCompleteSequence: Long?,
    val inspectedSequence: Long?,
    val verdictReadySequence: Long?,
    val deliveredSequence: Long,
)

internal data class ChromeCoverageLedgerSnapshot(
    val sessionId: String,
    val stateSequence: Long,
    val navigationSequence: Long,
    val events: List<ChromeCoverageResourceEvent>,
    val droppedEvents: Long,
)

internal data class ChromeVisibleMediaClaim(
    val instanceId: String,
    val stateSequence: Long,
    val navigationSequence: Long,
    val requestUrlHash: String? = null,
    val bodyDigest: String? = null,
    val correlationId: String? = null,
    val nonInterceptableReason: ChromeNonInterceptableReason? = null,
    val repeatedBodyIdentityProven: Boolean = false,
)

internal data class ChromeVisibleMediaClassification(
    val instanceId: String,
    val coverage: ChromeVisibleMediaCoverage,
    val reason: String,
    val correlationId: String? = null,
)

/**
 * Bounded DEV-only ledger for audit 17. It records identities and decisions, never response bodies,
 * decoded pixels, full URLs, cookies, authorization values, or prepared RGB.
 */
internal class ChromeRealWebProvenanceLedger(
    private val maximumEvents: Int = DefaultMaximumEvents,
    private val emit: (String) -> Unit = {},
) : AutoCloseable {
    private val lock = Any()
    private var sessionId = ""
    private var stateSequence = 0L
    private var navigationSequence = 0L
    private var requestSequence = 0L
    private var logicalSequence = 0L
    private var droppedEvents = 0L
    private var closed = false
    private val events = ArrayDeque<ChromeCoverageResourceEvent>(maximumEvents)
    private val pendingRedirects = LinkedHashMap<String, String>()

    init {
        require(maximumEvents > 0)
    }

    fun beginSession(value: String) {
        synchronized(lock) {
            require(value.isNotBlank())
            sessionId = value
            stateSequence = 0L
            navigationSequence = 0L
            requestSequence = 0L
            logicalSequence = 0L
            droppedEvents = 0L
            closed = false
            events.clear()
            pendingRedirects.clear()
        }
    }

    fun markState(
        label: String,
        newNavigation: Boolean,
    ): Long =
        synchronized(lock) {
            check(!closed && sessionId.isNotBlank())
            stateSequence += 1
            if (newNavigation) {
                navigationSequence += 1
                pendingRedirects.clear()
            }
            emit(
                "audit17 event=state session=${sessionId.take(SessionLogLength)} " +
                    "state=$stateSequence navigation=$navigationSequence " +
                    "label=${label.safeAuditToken(MaximumStateLabelLength)}",
            )
            stateSequence
        }

    fun beginRequest(
        host: String,
        request: ChromePhotosProxyRequest,
        correlationId: String,
    ): ChromeCoverageRequestToken =
        synchronized(lock) {
            check(!closed && sessionId.isNotBlank())
            requestSequence += 1
            val canonicalUrl = canonicalUrl(host, request.target)
            val requestUrlHash = sha256(canonicalUrl.toByteArray(Charsets.UTF_8))
            val referer = request.firstHeader("Referer")
            ChromeCoverageRequestToken(
                sessionId = sessionId,
                stateSequence = stateSequence,
                navigationSequence = navigationSequence,
                requestSequence = requestSequence,
                correlationId = correlationId.safeAuditToken(MaximumCorrelationLength),
                requestUrlHash = requestUrlHash,
                canonicalHost = host.lowercase(Locale.US),
                hostHash = sha256(host.lowercase(Locale.US).toByteArray(Charsets.UTF_8)),
                originClass = originClass(host, referer),
                refererHash = referer?.let { sha256(it.toByteArray(Charsets.UTF_8)) },
                destination = request.firstHeader("Sec-Fetch-Dest").safeAuditToken(MaximumDestinationLength),
                redirectFromCorrelationId = pendingRedirects.remove(requestUrlHash),
                observedSequence = ++logicalSequence,
            )
        }

    fun recordInspected(
        token: ChromeCoverageRequestToken,
        statusCode: Int,
        declaredContentType: String?,
        response: ChromePhotosSanitizedResponse,
    ) {
        val decisionResult = response.decisionResult
        val authoritative = response.decision in AuthoritativeDecisions
        record(
            ChromeCoverageResourceEvent(
                token = token,
                outcome =
                    if (authoritative) {
                        ChromeCoverageResourceOutcome.Inspected
                    } else {
                        ChromeCoverageResourceOutcome.FailClosed
                    },
                statusCode = statusCode,
                declaredContentType = declaredContentType.safeLogContentType(),
                canonicalContentType = response.contentType.safeLogContentType(),
                bodyDigest = response.observedBodyDigest ?: response.contentHash,
                byteLength = response.inputBytes,
                redirectTargetHash = null,
                decision = response.decision.name.lowercase(Locale.US),
                reason = decisionResult?.reason.orEmpty().safeAuditToken(MaximumReasonLength),
                verdictSource = decisionResult?.source?.name?.lowercase(Locale.US).orEmpty(),
                verdictCacheHit = response.cacheHit,
                bodyCompleteSequence = nextSequence(),
                inspectedSequence = nextSequence(),
                verdictReadySequence = nextSequence(),
                deliveredSequence = nextSequence(),
            ),
        )
    }

    fun recordRedirect(
        token: ChromeCoverageRequestToken,
        statusCode: Int,
        location: String?,
    ) {
        val targetHash = location?.let { redirectTargetHash(token, it) }
        synchronized(lock) {
            if (targetHash != null) pendingRedirects[targetHash] = token.correlationId
        }
        record(
            ChromeCoverageResourceEvent(
                token = token,
                outcome = ChromeCoverageResourceOutcome.Redirect,
                statusCode = statusCode,
                declaredContentType = "",
                canonicalContentType = "",
                bodyDigest = null,
                byteLength = 0,
                redirectTargetHash = targetHash,
                decision = "redirect",
                reason = if (targetHash == null) "redirect_target_unavailable" else "redirect_observed",
                verdictSource = "",
                verdictCacheHit = false,
                bodyCompleteSequence = null,
                inspectedSequence = null,
                verdictReadySequence = null,
                deliveredSequence = nextSequence(),
            ),
        )
    }

    fun recordFailure(
        token: ChromeCoverageRequestToken,
        reason: String,
    ) {
        record(
            ChromeCoverageResourceEvent(
                token = token,
                outcome = ChromeCoverageResourceOutcome.Failure,
                statusCode = 0,
                declaredContentType = "",
                canonicalContentType = "",
                bodyDigest = null,
                byteLength = 0,
                redirectTargetHash = null,
                decision = "failure",
                reason = reason.safeAuditToken(MaximumReasonLength),
                verdictSource = "",
                verdictCacheHit = false,
                bodyCompleteSequence = null,
                inspectedSequence = null,
                verdictReadySequence = null,
                deliveredSequence = nextSequence(),
            ),
        )
    }

    fun snapshot(): ChromeCoverageLedgerSnapshot =
        synchronized(lock) {
            ChromeCoverageLedgerSnapshot(
                sessionId = sessionId,
                stateSequence = stateSequence,
                navigationSequence = navigationSequence,
                events = events.toList(),
                droppedEvents = droppedEvents,
            )
        }

    fun classify(claims: List<ChromeVisibleMediaClaim>): List<ChromeVisibleMediaClassification> {
        val snapshot = snapshot()
        val consumed = mutableSetOf<String>()
        return claims.map { claim -> classifyOne(claim, snapshot.events, consumed) }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            sessionId = ""
            events.clear()
            pendingRedirects.clear()
        }
    }

    private fun classifyOne(
        claim: ChromeVisibleMediaClaim,
        recorded: List<ChromeCoverageResourceEvent>,
        consumed: MutableSet<String>,
    ): ChromeVisibleMediaClassification {
        claim.nonInterceptableReason?.let { reason ->
            return ChromeVisibleMediaClassification(
                instanceId = claim.instanceId,
                coverage = ChromeVisibleMediaCoverage.DefiniteNonInterceptable,
                reason = reason.name,
            )
        }
        if (claim.requestUrlHash == null || claim.bodyDigest == null) {
            return claim.unknown("missing_exact_visible_identity")
        }
        val candidates =
            recorded.filter { event ->
                event.token.stateSequence == claim.stateSequence &&
                    event.token.navigationSequence == claim.navigationSequence &&
                    event.token.requestUrlHash == claim.requestUrlHash &&
                    event.bodyDigest == claim.bodyDigest &&
                    event.outcome in AuthoritativeOutcomes &&
                    (claim.correlationId == null || event.token.correlationId == claim.correlationId)
            }
        val chosen =
            when {
                candidates.size == 1 -> candidates.single()
                claim.correlationId != null -> candidates.singleOrNull()
                else -> null
            } ?: return claim.unknown("no_unique_authoritative_resource")
        if (!claim.repeatedBodyIdentityProven && !consumed.add(chosen.token.correlationId)) {
            return claim.unknown("visible_instance_reuse_not_proven")
        }
        return ChromeVisibleMediaClassification(
            instanceId = claim.instanceId,
            coverage = ChromeVisibleMediaCoverage.AuthoritativePreRender,
            reason = "exact_request_and_body_before_delivery",
            correlationId = chosen.token.correlationId,
        )
    }

    private fun ChromeVisibleMediaClaim.unknown(reason: String) =
        ChromeVisibleMediaClassification(
            instanceId = instanceId,
            coverage = ChromeVisibleMediaCoverage.AttributionUnknown,
            reason = reason,
        )

    private fun record(event: ChromeCoverageResourceEvent) {
        synchronized(lock) {
            if (closed || event.token.sessionId != sessionId) return
            if (events.size == maximumEvents) {
                events.removeFirst()
                droppedEvents += 1
            }
            events.addLast(event)
        }
        emit(event.logLine())
    }

    private fun nextSequence(): Long = synchronized(lock) { ++logicalSequence }

    private fun redirectTargetHash(
        token: ChromeCoverageRequestToken,
        location: String,
    ): String? =
        runCatching {
            val base = URI("https://${token.canonicalHost}/")
            val target =
                if (location.startsWith('/')) {
                    URI("https://${token.canonicalHost}$location")
                } else {
                    base.resolve(location)
                }
            val canonical =
                "https://${target.host.lowercase(Locale.US)}${target.rawPath.orEmpty()}" +
                    target.rawQuery?.let { "?$it" }.orEmpty()
            sha256(canonical.toByteArray(Charsets.UTF_8))
        }.getOrNull()

    private fun ChromeCoverageResourceEvent.logLine(): String =
        "audit17 event=resource session=${token.sessionId.take(SessionLogLength)} " +
            "state=${token.stateSequence} navigation=${token.navigationSequence} " +
            "request=${token.requestSequence} correlation=${token.correlationId} " +
            "urlHash=${token.requestUrlHash} hostHash=${token.hostHash} originClass=${token.originClass} " +
            "refererHash=${token.refererHash.orEmpty()} dest=${token.destination} " +
            "redirectFrom=${token.redirectFromCorrelationId.orEmpty()} outcome=${outcome.name.lowercase(Locale.US)} " +
            "status=$statusCode declared=$declaredContentType canonical=$canonicalContentType " +
            "bodySha=${bodyDigest.orEmpty()} bytes=$byteLength decision=$decision reason=$reason " +
            "source=$verdictSource cache=${if (verdictCacheHit) "hit" else "miss"} " +
            "observed=${token.observedSequence} bodyComplete=${bodyCompleteSequence ?: 0} " +
            "inspected=${inspectedSequence ?: 0} verdictReady=${verdictReadySequence ?: 0} " +
            "delivered=$deliveredSequence redirectTarget=${redirectTargetHash.orEmpty()}"

    private fun canonicalUrl(
        host: String,
        target: String,
    ): String = "https://${host.lowercase(Locale.US)}${target.substringBefore('#')}"

    private fun originClass(
        host: String,
        referer: String?,
    ): String {
        val refererHost = referer?.let { runCatching { URI(it).host }.getOrNull() } ?: return "no_referer"
        return if (refererHost.equals(host, ignoreCase = true)) "same_origin" else "cross_origin"
    }

    private companion object {
        const val DefaultMaximumEvents = 512
        const val SessionLogLength = 8
        const val MaximumStateLabelLength = 48
        const val MaximumCorrelationLength = 48
        const val MaximumDestinationLength = 24
        const val MaximumReasonLength = 64
        val AuthoritativeDecisions = setOf(ChromePhotosResourceDecision.Safe, ChromePhotosResourceDecision.Block)
        val AuthoritativeOutcomes =
            setOf(
                ChromeCoverageResourceOutcome.Inspected,
                ChromeCoverageResourceOutcome.FailClosed,
            )
    }
}

private fun String?.safeAuditToken(maximumLength: Int): String =
    this
        .orEmpty()
        .filter { character -> character.isLetterOrDigit() || character in "_.:-" }
        .take(maximumLength)
