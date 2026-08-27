package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryNativeGeneration
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryRenderBinding
import java.security.MessageDigest

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
    val generationReplacements: Long,
)

internal sealed interface ChromeVisualShieldRegionDiscoveryHandshakeRequestResult {
    val renderKeyDigest: String
    val metrics: ChromeVisualShieldRegionDiscoveryHandshakeMetrics

    data class NewRenderRequired(
        val binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
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
    val binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
)

internal class ChromeVisualShieldRegionDiscoveryHandshakePolicy {
    private data class Active(
        val requestSequence: Long,
        val key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        val binding: ChromeVisualShieldRegionDiscoveryRenderBinding?,
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
    private var generationReplacements = 0L

    @Synchronized
    fun request(
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration,
        key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        beginFixtureRender: () -> ChromeVisualShieldRegionDiscoveryRenderBinding?,
    ): ChromeVisualShieldRegionDiscoveryHandshakeRequestResult {
        requests += 1
        active?.takeIf { it.binding?.matches(nativeGeneration) == true && it.key == key }?.let { current ->
            reused += 1
            return ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse(
                phase = current.phase,
                renderKeyDigest = current.key.digest,
                metrics = metrics(),
            )
        }

        if (active?.key == key && active?.binding?.matches(nativeGeneration) == false) {
            generationReplacements += 1
        }
        requestSequence += 1
        beginFixtureRenderCount += 1
        val binding = beginFixtureRender()
        val phase =
            if (
                binding != null &&
                binding.isStructurallyValid() &&
                binding.renderGeometryKeyDigest == key.digest &&
                binding.protectionSessionId == nativeGeneration.protectionSessionId &&
                binding.windowId == nativeGeneration.windowId
            ) {
                ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight
            } else {
                ChromeVisualShieldRegionDiscoveryHandshakePhase.Rejected
            }
        active = Active(requestSequence, key, binding, phase)
        return if (phase == ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight) {
            ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired(
                binding = requireNotNull(binding),
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
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration,
        claimedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    ): ChromeVisualShieldRegionDiscoveryAttestationClaim? {
        val current = active
        if (
            current == null ||
            current.binding != claimedBinding ||
            !claimedBinding.matches(nativeGeneration) ||
            current.key.digest != claimedBinding.renderGeometryKeyDigest ||
            current.phase != ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight
        ) {
            staleAttestationDropped += 1
            return null
        }
        attestationClaims += 1
        active = current.copy(phase = ChromeVisualShieldRegionDiscoveryHandshakePhase.Attesting)
        return ChromeVisualShieldRegionDiscoveryAttestationClaim(
            requestSequence = current.requestSequence,
            binding = claimedBinding,
        )
    }

    @Synchronized
    fun executeAttestation(
        claim: ChromeVisualShieldRegionDiscoveryAttestationClaim,
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration?,
        action: () -> Boolean,
    ): Boolean? {
        val current = active
        if (
            current == null ||
            current.requestSequence != claim.requestSequence ||
            current.binding != claim.binding ||
            nativeGeneration == null ||
            !claim.binding.matches(nativeGeneration) ||
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
            generationReplacements = generationReplacements,
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
        generationReplacements = 0L
    }
}

internal object ChromeVisualShieldRegionDiscoveryHandshakeStore {
    private val policy = ChromeVisualShieldRegionDiscoveryHandshakePolicy()

    fun request(
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration,
        key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        beginFixtureRender: () -> ChromeVisualShieldRegionDiscoveryRenderBinding?,
    ): ChromeVisualShieldRegionDiscoveryHandshakeRequestResult =
        policy.request(nativeGeneration, key, beginFixtureRender)

    fun claimAttestation(
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration,
        claimedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    ): ChromeVisualShieldRegionDiscoveryAttestationClaim? = policy.claimAttestation(nativeGeneration, claimedBinding)

    fun executeAttestation(
        claim: ChromeVisualShieldRegionDiscoveryAttestationClaim,
        nativeGeneration: ChromeVisualShieldRegionDiscoveryNativeGeneration?,
        action: () -> Boolean,
    ): Boolean? = policy.executeAttestation(claim, nativeGeneration, action)

    fun metrics(): ChromeVisualShieldRegionDiscoveryHandshakeMetrics = policy.metrics()

    fun clear() = policy.clear()
}

private fun Double.hex(): String = java.lang.Double.toHexString(this)

private fun renderKeySha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
