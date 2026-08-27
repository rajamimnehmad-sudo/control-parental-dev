package com.contentfilter.user.chromedataplane

import java.security.MessageDigest

internal data class ChromeVisualShieldRegionDiscoveryNativeSession(
    val protectionSessionId: Long,
    val windowId: Int,
) {
    companion object {
        fun fromRenderIdentityToken(token: String): ChromeVisualShieldRegionDiscoveryNativeSession? {
            val fields = token.split(':', limit = 3)
            if (fields.size < 3) return null
            return ChromeVisualShieldRegionDiscoveryNativeSession(
                protectionSessionId = fields[0].toLongOrNull() ?: return null,
                windowId = fields[1].toIntOrNull() ?: return null,
            )
        }
    }
}

internal data class ChromeVisualShieldRegionDiscoveryRenderGeometryKey(
    val scenarioId: String,
    val layoutContract: String,
    val sourceSha256s: List<String>,
    val orientation: String,
    val visualViewportOffsetLeft: Double,
    val visualViewportOffsetTop: Double,
    val visualViewportWidth: Double,
    val visualViewportHeight: Double,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
    val canvasCssLeft: Double,
    val canvasCssTop: Double,
    val canvasCssWidth: Double,
    val canvasCssHeight: Double,
    val canvasBackingWidth: Int,
    val canvasBackingHeight: Int,
) {
    val digest: String
        get() = renderKeySha256(canonical().toByteArray(Charsets.UTF_8))

    fun isValidFor(scenario: ChromeVisualShieldRegionDiscoveryScenario): Boolean =
        scenarioId == scenario.wireName &&
            layoutContract == ChromeVisualShieldRegionDiscoveryLayoutContract.Version &&
            sourceSha256s == scenario.samples.map { it.expectedSha256 } &&
            orientation.matches(OrientationPattern) &&
            listOf(
                visualViewportOffsetLeft,
                visualViewportOffsetTop,
                visualViewportWidth,
                visualViewportHeight,
                visualViewportScale,
                devicePixelRatio,
                canvasCssLeft,
                canvasCssTop,
                canvasCssWidth,
                canvasCssHeight,
            ).all(Double::isFinite) &&
            visualViewportWidth > 0.0 &&
            visualViewportHeight > 0.0 &&
            visualViewportScale > 0.0 &&
            devicePixelRatio > 0.0 &&
            canvasCssWidth > 0.0 &&
            canvasCssHeight > 0.0 &&
            canvasBackingWidth in 1..MaximumBackingDimension &&
            canvasBackingHeight in 1..MaximumBackingDimension

    private fun canonical(): String =
        listOf(
            scenarioId,
            layoutContract,
            sourceSha256s.joinToString(","),
            orientation,
            visualViewportOffsetLeft.hex(),
            visualViewportOffsetTop.hex(),
            visualViewportWidth.hex(),
            visualViewportHeight.hex(),
            visualViewportScale.hex(),
            devicePixelRatio.hex(),
            canvasCssLeft.hex(),
            canvasCssTop.hex(),
            canvasCssWidth.hex(),
            canvasCssHeight.hex(),
            canvasBackingWidth,
            canvasBackingHeight,
        ).joinToString("|")

    companion object {
        fun parse(
            scenario: ChromeVisualShieldRegionDiscoveryScenario,
            body: String,
        ): ChromeVisualShieldRegionDiscoveryRenderGeometryKey? {
            val fields = body.split('|')
            if (fields.size != FieldCount) return null
            val value =
                ChromeVisualShieldRegionDiscoveryRenderGeometryKey(
                    scenarioId = fields[0],
                    layoutContract = fields[1],
                    sourceSha256s = fields[2].split(',').filter(String::isNotEmpty),
                    orientation = fields[3],
                    visualViewportOffsetLeft = fields[4].toDoubleOrNull() ?: return null,
                    visualViewportOffsetTop = fields[5].toDoubleOrNull() ?: return null,
                    visualViewportWidth = fields[6].toDoubleOrNull() ?: return null,
                    visualViewportHeight = fields[7].toDoubleOrNull() ?: return null,
                    visualViewportScale = fields[8].toDoubleOrNull() ?: return null,
                    devicePixelRatio = fields[9].toDoubleOrNull() ?: return null,
                    canvasCssLeft = fields[10].toDoubleOrNull() ?: return null,
                    canvasCssTop = fields[11].toDoubleOrNull() ?: return null,
                    canvasCssWidth = fields[12].toDoubleOrNull() ?: return null,
                    canvasCssHeight = fields[13].toDoubleOrNull() ?: return null,
                    canvasBackingWidth = fields[14].toIntOrNull() ?: return null,
                    canvasBackingHeight = fields[15].toIntOrNull() ?: return null,
                )
            return value.takeIf { it.isValidFor(scenario) }
        }

        private val OrientationPattern = Regex("(?:portrait|landscape)(?:-(?:primary|secondary))?")
        private const val FieldCount = 16
        private const val MaximumBackingDimension = 16_384
    }
}

internal enum class ChromeVisualShieldRegionDiscoveryHandshakePhase {
    InFlight,
    Attesting,
    Attested,
    Rejected,
}

internal data class ChromeVisualShieldRegionDiscoveryHandshakeMetrics(
    val requests: Long,
    val beginFixtureRenderCount: Long,
    val reused: Long,
    val attestationClaims: Long,
    val attestationAccepted: Long,
    val attestationRejected: Long,
    val staleAttestationDropped: Long,
)

internal sealed interface ChromeVisualShieldRegionDiscoveryHandshakeRequestResult {
    val renderKeyDigest: String
    val metrics: ChromeVisualShieldRegionDiscoveryHandshakeMetrics

    data class NewRenderRequired(
        val renderIdentityToken: String,
        override val renderKeyDigest: String,
        override val metrics: ChromeVisualShieldRegionDiscoveryHandshakeMetrics,
    ) : ChromeVisualShieldRegionDiscoveryHandshakeRequestResult

    data class Reuse(
        val phase: ChromeVisualShieldRegionDiscoveryHandshakePhase,
        override val renderKeyDigest: String,
        override val metrics: ChromeVisualShieldRegionDiscoveryHandshakeMetrics,
    ) : ChromeVisualShieldRegionDiscoveryHandshakeRequestResult

    data class Rejected(
        val reason: String,
        override val renderKeyDigest: String,
        override val metrics: ChromeVisualShieldRegionDiscoveryHandshakeMetrics,
    ) : ChromeVisualShieldRegionDiscoveryHandshakeRequestResult
}

internal data class ChromeVisualShieldRegionDiscoveryAttestationClaim(
    val requestSequence: Long,
    val renderIdentityToken: String,
    val renderKeyDigest: String,
)

internal class ChromeVisualShieldRegionDiscoveryHandshakePolicy {
    private data class Active(
        val requestSequence: Long,
        val nativeSession: ChromeVisualShieldRegionDiscoveryNativeSession,
        val key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        val renderIdentityToken: String?,
        val phase: ChromeVisualShieldRegionDiscoveryHandshakePhase,
    )

    private var active: Active? = null
    private var requestSequence = 0L
    private var requests = 0L
    private var beginFixtureRenderCount = 0L
    private var reused = 0L
    private var attestationClaims = 0L
    private var attestationAccepted = 0L
    private var attestationRejected = 0L
    private var staleAttestationDropped = 0L

    @Synchronized
    fun request(
        nativeSession: ChromeVisualShieldRegionDiscoveryNativeSession,
        key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        beginFixtureRender: () -> String?,
    ): ChromeVisualShieldRegionDiscoveryHandshakeRequestResult {
        requests += 1
        active?.takeIf { it.nativeSession == nativeSession && it.key == key }?.let { current ->
            reused += 1
            return ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse(
                phase = current.phase,
                renderKeyDigest = current.key.digest,
                metrics = metrics(),
            )
        }

        requestSequence += 1
        beginFixtureRenderCount += 1
        val renderIdentityToken = beginFixtureRender()
        val returnedSession =
            renderIdentityToken?.let(ChromeVisualShieldRegionDiscoveryNativeSession::fromRenderIdentityToken)
        val phase =
            if (renderIdentityToken != null && returnedSession == nativeSession) {
                ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight
            } else {
                ChromeVisualShieldRegionDiscoveryHandshakePhase.Rejected
            }
        active = Active(requestSequence, nativeSession, key, renderIdentityToken, phase)
        return if (phase == ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight) {
            ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired(
                renderIdentityToken = requireNotNull(renderIdentityToken),
                renderKeyDigest = key.digest,
                metrics = metrics(),
            )
        } else {
            attestationRejected += 1
            ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Rejected(
                reason = "native_identity_changed",
                renderKeyDigest = key.digest,
                metrics = metrics(),
            )
        }
    }

    @Synchronized
    fun claimAttestation(
        nativeSession: ChromeVisualShieldRegionDiscoveryNativeSession,
        renderIdentityToken: String,
        renderKeyDigest: String,
    ): ChromeVisualShieldRegionDiscoveryAttestationClaim? {
        val current = active
        if (
            current == null ||
            current.nativeSession != nativeSession ||
            current.renderIdentityToken != renderIdentityToken ||
            current.key.digest != renderKeyDigest ||
            current.phase != ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight
        ) {
            staleAttestationDropped += 1
            return null
        }
        attestationClaims += 1
        active = current.copy(phase = ChromeVisualShieldRegionDiscoveryHandshakePhase.Attesting)
        return ChromeVisualShieldRegionDiscoveryAttestationClaim(
            requestSequence = current.requestSequence,
            renderIdentityToken = renderIdentityToken,
            renderKeyDigest = renderKeyDigest,
        )
    }

    @Synchronized
    fun executeAttestation(
        claim: ChromeVisualShieldRegionDiscoveryAttestationClaim,
        action: () -> Boolean,
    ): Boolean? {
        val current = active
        if (
            current == null ||
            current.requestSequence != claim.requestSequence ||
            current.renderIdentityToken != claim.renderIdentityToken ||
            current.key.digest != claim.renderKeyDigest ||
            current.phase != ChromeVisualShieldRegionDiscoveryHandshakePhase.Attesting
        ) {
            staleAttestationDropped += 1
            return null
        }
        val accepted = action()
        if (accepted) {
            attestationAccepted += 1
        } else {
            attestationRejected += 1
        }
        active =
            current.copy(
                phase =
                    if (accepted) {
                        ChromeVisualShieldRegionDiscoveryHandshakePhase.Attested
                    } else {
                        ChromeVisualShieldRegionDiscoveryHandshakePhase.Rejected
                    },
            )
        return accepted
    }

    @Synchronized
    fun metrics(): ChromeVisualShieldRegionDiscoveryHandshakeMetrics =
        ChromeVisualShieldRegionDiscoveryHandshakeMetrics(
            requests = requests,
            beginFixtureRenderCount = beginFixtureRenderCount,
            reused = reused,
            attestationClaims = attestationClaims,
            attestationAccepted = attestationAccepted,
            attestationRejected = attestationRejected,
            staleAttestationDropped = staleAttestationDropped,
        )

    @Synchronized
    fun clear() {
        active = null
        requestSequence = 0L
        requests = 0L
        beginFixtureRenderCount = 0L
        reused = 0L
        attestationClaims = 0L
        attestationAccepted = 0L
        attestationRejected = 0L
        staleAttestationDropped = 0L
    }
}

internal object ChromeVisualShieldRegionDiscoveryHandshakeStore {
    private val policy = ChromeVisualShieldRegionDiscoveryHandshakePolicy()

    fun request(
        nativeSession: ChromeVisualShieldRegionDiscoveryNativeSession,
        key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        beginFixtureRender: () -> String?,
    ): ChromeVisualShieldRegionDiscoveryHandshakeRequestResult = policy.request(nativeSession, key, beginFixtureRender)

    fun claimAttestation(
        nativeSession: ChromeVisualShieldRegionDiscoveryNativeSession,
        renderIdentityToken: String,
        renderKeyDigest: String,
    ): ChromeVisualShieldRegionDiscoveryAttestationClaim? =
        policy.claimAttestation(nativeSession, renderIdentityToken, renderKeyDigest)

    fun executeAttestation(
        claim: ChromeVisualShieldRegionDiscoveryAttestationClaim,
        action: () -> Boolean,
    ): Boolean? = policy.executeAttestation(claim, action)

    fun metrics(): ChromeVisualShieldRegionDiscoveryHandshakeMetrics = policy.metrics()

    fun clear() = policy.clear()
}

private fun Double.hex(): String = java.lang.Double.toHexString(this)

private fun renderKeySha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
