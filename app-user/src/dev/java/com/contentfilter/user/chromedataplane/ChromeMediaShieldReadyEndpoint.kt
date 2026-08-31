package com.contentfilter.user.chromedataplane

import android.os.SystemClock
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeResult
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentPhase
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierResult
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaimResult
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

internal data class ChromeMediaShieldReadyEndpointMetrics(
    val requests: Long = 0L,
    val preflights: Long = 0L,
    val accepted: Long = 0L,
    val rejected: Long = 0L,
    val activeHello: Long = 0L,
    val challengeIssued: Long = 0L,
    val proofAccepted: Long = 0L,
    val presentAccepted: Long = 0L,
    val revokeAccepted: Long = 0L,
    val parserBarrierRequests: Long = 0L,
    val parserBarrierReady: Long = 0L,
    val parserBarrierFailClosed: Long = 0L,
    val selfReadyRequests: Long = 0L,
    val selfReadyAccepted: Long = 0L,
    val selfReadyRejected: Long = 0L,
)

/** Fixed-origin, capability-authenticated DEV endpoint. It never forwards READY traffic upstream. */
internal class ChromeMediaShieldReadyEndpoint(
    private val documentSelfShieldEnabled: Boolean = false,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val requests = AtomicLong()
    private val preflights = AtomicLong()
    private val accepted = AtomicLong()
    private val rejected = AtomicLong()
    private val activeHello = AtomicLong()
    private val challengeIssued = AtomicLong()
    private val proofAccepted = AtomicLong()
    private val presentAccepted = AtomicLong()
    private val revokeAccepted = AtomicLong()
    private val parserBarrierRequests = AtomicLong()
    private val parserBarrierReady = AtomicLong()
    private val parserBarrierFailClosed = AtomicLong()
    private val selfReadyRequests = AtomicLong()
    private val selfReadyAccepted = AtomicLong()
    private val selfReadyRejected = AtomicLong()

    fun handle(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse? {
        if (request.target == ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath) {
            return if (documentSelfShieldEnabled) handleSelfReady(request) else reject("self_ready_disabled")
        }
        if (request.target == ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierPath) {
            return if (documentSelfShieldEnabled) reject("parser_barrier_disabled") else handleParserBarrier(request)
        }
        if (request.target != ChromePhotosDataPlaneLabContract.MediaShieldReadyPath) return null
        if (documentSelfShieldEnabled) return reject("active_document_ready_disabled")
        requests.incrementAndGet()
        val body = request.body
        return try {
            val origin = request.exactReadyOriginOrNull() ?: return reject("ready_origin_invalid")
            if (origin == NullOrigin) return reject("ready_origin_not_network")
            if (request.method == OptionsMethod) return preflight(request, origin)
            if (request.method != PostMethod) return reject("ready_method_invalid", statusCode = 405)
            if (!request.hasExactReadyContentType()) return reject("ready_content_type_invalid")
            if (!request.hasReadyFetchMetadata()) return reject("ready_fetch_metadata_invalid")
            val parsed = body.parseReadyBody() ?: return reject("ready_body_invalid")
            handleActiveDocument(parsed, origin)
        } finally {
            body.fill(0)
        }
    }

    fun metrics(): ChromeMediaShieldReadyEndpointMetrics =
        ChromeMediaShieldReadyEndpointMetrics(
            requests = requests.get(),
            preflights = preflights.get(),
            accepted = accepted.get(),
            rejected = rejected.get(),
            activeHello = activeHello.get(),
            challengeIssued = challengeIssued.get(),
            proofAccepted = proofAccepted.get(),
            presentAccepted = presentAccepted.get(),
            revokeAccepted = revokeAccepted.get(),
            parserBarrierRequests = parserBarrierRequests.get(),
            parserBarrierReady = parserBarrierReady.get(),
            parserBarrierFailClosed = parserBarrierFailClosed.get(),
            selfReadyRequests = selfReadyRequests.get(),
            selfReadyAccepted = selfReadyAccepted.get(),
            selfReadyRejected = selfReadyRejected.get(),
        )

    private fun handleSelfReady(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse {
        selfReadyRequests.incrementAndGet()
        requests.incrementAndGet()
        val body = request.body
        return try {
            val origin = request.exactReadyOriginOrNull() ?: return rejectSelfReady("self_ready_origin_invalid")
            if (origin == NullOrigin) return rejectSelfReady("self_ready_origin_not_network")
            if (request.method == OptionsMethod) return preflight(request, origin)
            if (request.method != PostMethod) return rejectSelfReady("self_ready_method_invalid", 405)
            if (!request.hasExactReadyContentType()) return rejectSelfReady("self_ready_content_type_invalid")
            if (!request.hasReadyFetchMetadata()) return rejectSelfReady("self_ready_fetch_metadata_invalid")
            val parsed = body.parseSelfReadyBody() ?: return rejectSelfReady("self_ready_body_invalid")
            if (!runtimeAdmits(parsed.identity)) return rejectSelfReady("self_ready_runtime_unavailable")
            when (
                val result =
                    ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(
                        parsed.token,
                        parsed.identity,
                    )
            ) {
                is ChromeMediaShieldReadyClaimResult.Claimed -> {
                    selfReadyAccepted.incrementAndGet()
                    acceptNoContent(origin)
                }
                is ChromeMediaShieldReadyClaimResult.Invalid -> rejectSelfReady(result.reason)
            }
        } finally {
            body.fill(0)
        }
    }

    private fun runtimeAdmits(identity: ChromeMediaShieldSelfReadyIdentity): Boolean {
        val runtime = ChromePhotosDataPlaneRuntimeAttestation.snapshot()
        val now = elapsedRealtime()
        val scopeHeartbeat =
            when {
                runtime.fixtureConfirmed -> runtime.fixtureHeartbeatElapsed
                runtime.realWebScopeConfirmed -> runtime.realWebScopeHeartbeatElapsed
                else -> 0L
            }
        return runtime.documentSelfShieldEnabled &&
            runtime.mediaAuthorityEnabled &&
            runtime.sessionId == identity.protectionSessionId &&
            runtime.mediaPolicyEpoch == identity.policyEpoch &&
            runtime.proxyHealthy &&
            runtime.policyConfirmed &&
            runtime.vpnConfirmed &&
            runtime.vpnSessionId == runtime.sessionId &&
            runtime.heartbeatElapsed in 1..now &&
            runtime.validUntilElapsed > now &&
            scopeHeartbeat in 1..now &&
            now - scopeHeartbeat <= MaximumReadyHeartbeatAgeMillis
    }

    private fun rejectSelfReady(
        reason: String,
        statusCode: Int = 503,
    ): ChromePhotosSanitizedResponse {
        selfReadyRejected.incrementAndGet()
        return reject(reason, statusCode)
    }

    private fun handleParserBarrier(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse {
        parserBarrierRequests.incrementAndGet()
        request.body.fill(0)
        val admitted =
            request.method == GetMethod &&
                request.body.isEmpty() &&
                request.headerValues("Sec-Fetch-Dest").singleOrNull()?.equals("script", true) == true &&
                request.headerValues("Sec-Fetch-Mode").singleOrNull()?.equals("no-cors", true) == true
        if (!admitted) return parserBarrierResponse(ready = false)
        val ready = ChromeMediaShieldParserBarrierBridge.await() == ChromeMediaShieldParserBarrierResult.Ready
        return parserBarrierResponse(ready)
    }

    private fun parserBarrierResponse(ready: Boolean): ChromePhotosSanitizedResponse {
        if (ready) parserBarrierReady.incrementAndGet() else parserBarrierFailClosed.incrementAndGet()
        val script =
            if (ready) {
                "self.__gloshH19ParserBarrierCommit__&&self.__gloshH19ParserBarrierCommit__(true);"
            } else {
                "self.__gloshH19ParserBarrierCommit__&&self.__gloshH19ParserBarrierCommit__(false);"
            }
        return response(
            statusCode = 200,
            statusText = "OK",
            headers = BaseHeaders + ChromeHttpHeader("Content-Type", "application/javascript; charset=us-ascii"),
            bytes = script.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    private fun handleActiveDocument(
        parsed: ParsedReadyBody,
        origin: String,
    ): ChromePhotosSanitizedResponse {
        val claim =
            when (parsed.phase) {
                ChromeMediaShieldActiveDocumentPhase.Hello -> {
                    activeHello.incrementAndGet()
                    when (
                        val result =
                            ChromeMediaShieldDocumentAuthorityRegistry.claimTopLevelReady(
                                parsed.token,
                                parsed.lifecycleSequence,
                            )
                    ) {
                        is ChromeMediaShieldReadyClaimResult.Claimed -> result.claim
                        is ChromeMediaShieldReadyClaimResult.Invalid -> return reject(result.reason)
                    }
                }
                else ->
                    ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(
                        parsed.token,
                        parsed.lifecycleSequence,
                    ) ?: return reject("ready_claim_stale_or_invalid")
            }
        val request = parsed.toHandshakeRequest(claim) ?: return reject("ready_challenge_invalid")
        return when (val result = ChromeMediaShieldActiveDocumentHandshakeBridge.await(request)) {
            is ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued -> {
                challengeIssued.incrementAndGet()
                acceptChallenge(origin, result.challenge)
            }
            ChromeMediaShieldActiveDocumentHandshakeResult.ProofAccepted -> {
                proofAccepted.incrementAndGet()
                acceptNoContent(origin)
            }
            ChromeMediaShieldActiveDocumentHandshakeResult.PresentationAccepted -> {
                presentAccepted.incrementAndGet()
                acceptNoContent(origin)
            }
            ChromeMediaShieldActiveDocumentHandshakeResult.Revoked -> {
                revokeAccepted.incrementAndGet()
                acceptNoContent(origin)
            }
            ChromeMediaShieldActiveDocumentHandshakeResult.Rejected -> reject("ready_presentation_rejected")
            ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable -> reject("ready_presentation_unavailable")
            ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut -> reject("ready_presentation_timeout")
            ChromeMediaShieldActiveDocumentHandshakeResult.Interrupted -> reject("ready_presentation_interrupted")
        }
    }

    private fun acceptNoContent(origin: String): ChromePhotosSanitizedResponse {
        accepted.incrementAndGet()
        return response(
            statusCode = 204,
            statusText = "No Content",
            headers =
                BaseHeaders +
                    ChromeHttpHeader("Access-Control-Allow-Origin", origin) +
                    ChromeHttpHeader("Vary", "Origin"),
        )
    }

    private fun acceptChallenge(
        origin: String,
        challenge: ChromeMediaShieldActiveDocumentChallenge,
    ): ChromePhotosSanitizedResponse {
        accepted.incrementAndGet()
        val bytes = "v2|CHALLENGE|${challenge.encoded}".toByteArray(StandardCharsets.US_ASCII)
        return response(
            statusCode = 200,
            statusText = "OK",
            headers =
                BaseHeaders +
                    ChromeHttpHeader("Access-Control-Allow-Origin", origin) +
                    ChromeHttpHeader("Vary", "Origin") +
                    ChromeHttpHeader("Content-Type", "text/plain; charset=us-ascii"),
            bytes = bytes,
        )
    }

    private fun preflight(
        request: ChromePhotosProxyRequest,
        origin: String,
    ): ChromePhotosSanitizedResponse {
        if (
            request.body.isNotEmpty() ||
            !request.hasReadyFetchMetadata() ||
            request.headerValues("Access-Control-Request-Method").singleOrNull()
                ?.equals(PostMethod, ignoreCase = true) != true ||
            !request.hasExactPreflightHeaders()
        ) {
            return reject("ready_preflight_invalid")
        }
        preflights.incrementAndGet()
        val privateNetworkRequested =
            request.headerValues("Access-Control-Request-Private-Network").singleOrNull()
                ?.equals("true", ignoreCase = true) == true
        return response(
            statusCode = 204,
            statusText = "No Content",
            headers =
                BaseHeaders +
                    ChromeHttpHeader("Access-Control-Allow-Origin", origin) +
                    ChromeHttpHeader("Access-Control-Allow-Methods", PostMethod) +
                    ChromeHttpHeader("Access-Control-Allow-Headers", "Content-Type") +
                    ChromeHttpHeader("Access-Control-Max-Age", "0") +
                    (
                        if (privateNetworkRequested) {
                            listOf(ChromeHttpHeader("Access-Control-Allow-Private-Network", "true"))
                        } else {
                            emptyList()
                        }
                    ) +
                    ChromeHttpHeader("Vary", "Origin"),
        )
    }

    private fun reject(
        reason: String,
        statusCode: Int = 503,
    ): ChromePhotosSanitizedResponse {
        rejected.incrementAndGet()
        val bytes = reason.toByteArray(StandardCharsets.US_ASCII)
        return response(
            statusCode = statusCode,
            statusText = if (statusCode == 405) "Method Not Allowed" else "Service Unavailable",
            headers = BaseHeaders + ChromeHttpHeader("Content-Type", "text/plain; charset=us-ascii"),
            bytes = bytes,
        )
    }

    private fun response(
        statusCode: Int,
        statusText: String,
        headers: List<ChromeHttpHeader>,
        bytes: ByteArray = ByteArray(0),
    ) = ChromePhotosSanitizedResponse(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers,
        bytes = bytes,
        decision = ChromePhotosResourceDecision.Passthrough,
        cacheHit = false,
        contentHash = null,
        inputBytes = 0,
    )

    private data class ParsedReadyBody(
        val phase: ChromeMediaShieldActiveDocumentPhase,
        val token: String,
        val lifecycleSequence: Long,
        val challenge: ChromeMediaShieldActiveDocumentChallenge?,
    ) {
        fun toHandshakeRequest(claim: ChromeMediaShieldReadyClaim) =
            when (phase) {
                ChromeMediaShieldActiveDocumentPhase.Hello ->
                    ChromeMediaShieldActiveDocumentRequest.Hello(claim)
                ChromeMediaShieldActiveDocumentPhase.Prove ->
                    challenge?.let { ChromeMediaShieldActiveDocumentRequest.Prove(claim, it) }
                ChromeMediaShieldActiveDocumentPhase.Present ->
                    challenge?.let { ChromeMediaShieldActiveDocumentRequest.Present(claim, it) }
                ChromeMediaShieldActiveDocumentPhase.Revoke ->
                    challenge?.let { ChromeMediaShieldActiveDocumentRequest.Revoke(claim, it) }
            }
    }

    private data class ParsedSelfReadyBody(
        val token: String,
        val identity: ChromeMediaShieldSelfReadyIdentity,
    )

    private companion object {
        const val PostMethod = "POST"
        const val GetMethod = "GET"
        const val OptionsMethod = "OPTIONS"
        const val NullOrigin = "null"
        const val ReadyContentType = "text/plain;charset=UTF-8"
        const val MaximumReadyBodyBytes = 256
        const val MaximumReadyHeartbeatAgeMillis = 2_000L
        val BaseHeaders =
            listOf(
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("Cross-Origin-Resource-Policy", "cross-origin"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )

        fun ChromePhotosProxyRequest.exactReadyOriginOrNull(): String? {
            val values = headerValues("Origin")
            if (values.size != 1) return null
            val origin = values.single()
            if (origin == NullOrigin) return origin
            val uri = runCatching { URI(origin) }.getOrNull() ?: return null
            if (
                uri.isOpaque ||
                uri.scheme?.lowercase() !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                uri.rawPath?.isNotEmpty() == true ||
                uri.rawQuery != null ||
                uri.rawFragment != null
            ) {
                return null
            }
            return origin
        }

        fun ChromePhotosProxyRequest.hasExactReadyContentType(): Boolean {
            val values = headerValues("Content-Type")
            if (values.size != 1) return false
            return values.single().filterNot(Char::isWhitespace).equals(ReadyContentType, ignoreCase = true)
        }

        fun ChromePhotosProxyRequest.hasReadyFetchMetadata(): Boolean =
            headerValues("Sec-Fetch-Mode").singleOrNull()?.equals("cors", ignoreCase = true) == true &&
                headerValues("Sec-Fetch-Dest").singleOrNull()?.equals("empty", ignoreCase = true) == true

        fun ChromePhotosProxyRequest.hasExactPreflightHeaders(): Boolean {
            val values = headerValues("Access-Control-Request-Headers")
            if (values.isEmpty()) return true
            if (values.size != 1) return false
            val names = values.single().split(',').map(String::trim).filter(String::isNotEmpty)
            return names.size == 1 && names.single().equals("content-type", ignoreCase = true)
        }

        fun ByteArray.parseReadyBody(): ParsedReadyBody? {
            if (isEmpty() || size > MaximumReadyBodyBytes || any { it.toInt() !in 0x20..0x7e }) return null
            val parts = toString(StandardCharsets.US_ASCII).split('|')
            if (parts.size !in 4..5 || parts[0] != "v2") return null
            val phase =
                when (parts[1]) {
                    "HELLO" -> ChromeMediaShieldActiveDocumentPhase.Hello
                    "PROVE" -> ChromeMediaShieldActiveDocumentPhase.Prove
                    "PRESENT" -> ChromeMediaShieldActiveDocumentPhase.Present
                    "REVOKE" -> ChromeMediaShieldActiveDocumentPhase.Revoke
                    else -> return null
                }
            val token = parts[2]
            val lifecycle = parts[3].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val challenge =
                if (phase == ChromeMediaShieldActiveDocumentPhase.Hello) {
                    if (parts.size != 4) return null
                    null
                } else {
                    if (parts.size != 5) return null
                    runCatching { ChromeMediaShieldActiveDocumentChallenge.fromEncoded(parts[4]) }.getOrNull()
                        ?: return null
                }
            return ParsedReadyBody(phase, token, lifecycle, challenge)
        }

        fun ByteArray.parseSelfReadyBody(): ParsedSelfReadyBody? {
            if (isEmpty() || size > MaximumReadyBodyBytes || any { it.toInt() !in 0x20..0x7e }) return null
            val parts = toString(StandardCharsets.US_ASCII).split('|')
            if (parts.size != 9 || parts[0] != "v3" || parts[1] != "SELF_READY") return null
            val identity =
                ChromeMediaShieldSelfReadyIdentity(
                    protectionSessionId = parts[3].takeIf(String::isNotBlank) ?: return null,
                    policyEpoch = parts[4].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    navigationSequence = parts[5].toLongOrNull()?.takeIf { it >= 0L } ?: return null,
                    documentSequence = parts[6].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    lifecycleSequence = parts[7].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    topLevel =
                        when (parts[8]) {
                            "T" -> true
                            "S" -> false
                            else -> return null
                        },
                )
            return ParsedSelfReadyBody(parts[2], identity)
        }
    }
}
