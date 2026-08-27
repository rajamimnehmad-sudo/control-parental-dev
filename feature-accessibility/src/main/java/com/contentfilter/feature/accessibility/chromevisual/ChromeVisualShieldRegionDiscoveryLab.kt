package com.contentfilter.feature.accessibility.chromevisual

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds only DEV R2A probe state, its generation barrier, and post-discovery oracle comparison. */
internal class ChromeVisualShieldRegionDiscoveryLab(
    private val authority: ChromeVisualShieldRegionDiscoveryAuthority,
) {
    private data class PendingRender(
        val request: ChromeVisualShieldRegionDiscoveryProbeRequest,
        val binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    )

    private data class AcceptedRender(
        val request: ChromeVisualShieldRegionDiscoveryProbeRequest,
        val binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        val oracle: ChromeVisualShieldRegionDiscoveryOracle,
    )

    private class GenerationSignal {
        val latch = CountDownLatch(1)

        @Volatile
        var outcome: ChromeVisualShieldRegionDiscoveryGenerationOutcome? = null

        fun complete(value: ChromeVisualShieldRegionDiscoveryGenerationOutcome) {
            if (outcome != null) return
            outcome = value
            latch.countDown()
        }
    }

    private var request: ChromeVisualShieldRegionDiscoveryProbeRequest? = null
    private var pending: PendingRender? = null
    private var accepted: AcceptedRender? = null
    private var completed = false
    private var observation: ChromeVisualShieldRegionDiscoveryObservation? = null
    private val signals = mutableMapOf<ChromeVisualShieldRegionDiscoveryRenderBinding, GenerationSignal>()

    @Synchronized
    fun begin(value: ChromeVisualShieldRegionDiscoveryProbeRequest?) {
        completeOutstanding(ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped)
        request = value
        pending = null
        accepted = null
        completed = false
        observation = null
        signals.clear()
    }

    @Synchronized
    fun clear() {
        completeOutstanding(ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped)
        request = null
        pending = null
        accepted = null
        completed = false
        observation = null
    }

    @Synchronized
    fun invalidate() {
        pending?.binding?.let { complete(it, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated) }
        accepted?.binding?.let { complete(it, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated) }
        pending = null
        accepted = null
        completed = false
        observation = null
    }

    @Synchronized
    fun isActive(): Boolean = request != null

    @Synchronized
    fun isCompleted(): Boolean = completed

    @Synchronized
    fun recordRenderBinding(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        current: ChromeVisualShieldContext,
    ): Boolean {
        val currentRequest = request ?: return false
        if (!binding.isStructurallyValid() || !binding.matches(current)) return false
        pending?.binding?.let { complete(it, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated) }
        accepted?.binding?.let { complete(it, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated) }
        signals[binding] = GenerationSignal()
        pending = PendingRender(currentRequest, binding)
        accepted = null
        completed = false
        observation = null
        return true
    }

    @Synchronized
    fun acceptsAttestation(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        identity: ChromeVisualShieldIdentity,
        candidate: ChromeVisualShieldRegionDiscoveryOracle?,
    ): Boolean {
        val currentPending = pending ?: return false
        val value = candidate ?: return false
        return currentPending.binding == binding &&
            currentPending.request == request &&
            binding.matches(identity) &&
            value.matches(currentPending.request, binding, identity)
    }

    @Synchronized
    fun recordAttestation(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        identity: ChromeVisualShieldIdentity,
        candidate: ChromeVisualShieldRegionDiscoveryOracle?,
    ): Boolean {
        if (!acceptsAttestation(binding, identity, candidate)) return false
        val currentPending = checkNotNull(pending)
        accepted = AcceptedRender(currentPending.request, binding, checkNotNull(candidate))
        pending = null
        return true
    }

    @Synchronized
    fun hasCurrentBinding(context: ChromeVisualShieldContext): Boolean =
        accepted?.binding?.matches(context) == true

    @Synchronized
    fun workModeFor(identity: ChromeVisualShieldIdentity): ChromeVisualShieldWorkMode.RegionDiscoveryProbe? {
        val current = accepted ?: return null
        if (!current.binding.matches(identity)) return null
        return ChromeVisualShieldWorkMode.RegionDiscoveryProbe(current.request, current.binding)
    }

    fun awaitGeneration(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        timeoutMillis: Long,
    ): ChromeVisualShieldRegionDiscoveryGenerationOutcome {
        val signal = synchronized(this) { signals[binding] }
            ?: return ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated
        val completedInTime = signal.latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        return synchronized(this) {
            if (completedInTime) {
                signals.remove(binding, signal)
                return@synchronized signal.outcome
                    ?: ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated
            }
            if (signal.outcome == null) {
                signal.complete(ChromeVisualShieldRegionDiscoveryGenerationOutcome.TimedOut)
                if (pending?.binding == binding) pending = null
                if (accepted?.binding == binding) accepted = null
            }
            signals.remove(binding, signal)
            signal.outcome ?: ChromeVisualShieldRegionDiscoveryGenerationOutcome.TimedOut
        }
    }

    @Synchronized
    fun deliver(delivery: ChromeVisualShieldRegionDiscoveryDelivery): String {
        val mode = delivery.work.mode as? ChromeVisualShieldWorkMode.RegionDiscoveryProbe
            ?: return "phase=region_discovery_probe result=unexpected_mode"
        val current = accepted
        if (current == null || current.binding != mode.binding || !mode.binding.matches(delivery.work.identity)) {
            complete(mode.binding, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated)
            return "phase=region_discovery_probe result=stale_binding neverRelease=true rawPresented=false"
        }
        val authorityResult = authority.observe(delivery)
        val oracleVerification =
            ChromeVisualShieldRegionDiscoveryOracleVerifier.verify(
                identity = delivery.work.identity,
                searchEnvelope = delivery.searchEnvelope,
                crop = delivery.cropEvidence,
                request = current.request,
                oracle = current.oracle,
                discovery = delivery.discovery,
            )
        val oracleMatch = oracleVerification.matches
        completed =
            oracleMatch &&
            authorityResult != ChromeVisualShieldRegionDiscoveryAuthorityResult.StaleDropped &&
            authorityResult != ChromeVisualShieldRegionDiscoveryAuthorityResult.IdentityMismatchRejected
        observation =
            ChromeVisualShieldRegionDiscoveryObservation(
                request = current.request,
                identity = delivery.work.identity,
                searchEnvelope = delivery.searchEnvelope,
                crop = delivery.cropEvidence,
                discovery = delivery.discovery,
                decisions = delivery.decisions,
                authorityResult = authorityResult,
                oracleMatch = oracleMatch,
                oracleVerification = oracleVerification,
            )
        complete(mode.binding, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Completed)
        return logValue(checkNotNull(observation))
    }

    @Synchronized
    fun statusValue(): String {
        val current = observation
        val result =
            when (current?.discovery) {
                is ChromeVisualShieldRegionDiscoveryResult.Complete -> "complete"
                is ChromeVisualShieldRegionDiscoveryResult.Unknown -> "unknown"
                null -> "none"
            }
        val digest =
            if (completed) {
                (current?.discovery as? ChromeVisualShieldRegionDiscoveryResult.Complete)?.regionSetDigest ?: "none"
            } else {
                "none"
            }
        val binding = accepted?.binding ?: pending?.binding
        return "regionDiscoveryActive=${request != null} regionDiscoveryCompleted=$completed " +
            "regionDiscoveryResult=$result regionSetDigest=$digest regionOracleMatch=${current?.oracleMatch} " +
            "regionBindingContentEpoch=${binding?.contentEpoch} " +
            "regionBindingViewportEpoch=${binding?.viewportEpoch} " +
            "regionBindingRegionSequence=${binding?.regionSequence}"
    }

    @Synchronized
    private fun completeOutstanding(outcome: ChromeVisualShieldRegionDiscoveryGenerationOutcome) {
        signals.values.forEach { it.complete(outcome) }
    }

    @Synchronized
    private fun complete(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        outcome: ChromeVisualShieldRegionDiscoveryGenerationOutcome,
    ) {
        signals[binding]?.complete(outcome)
    }

    private fun ChromeVisualShieldRegionDiscoveryOracle.matches(
        expectedRequest: ChromeVisualShieldRegionDiscoveryProbeRequest,
        expectedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        identity: ChromeVisualShieldIdentity,
    ): Boolean =
        isStructurallyValid() &&
            renderIdentityToken == expectedBinding.renderIdentityToken &&
            renderIdentityToken == identity.renderIdentityToken() &&
            scenarioId == expectedRequest.scenarioId &&
            renderContract == expectedRequest.renderContract &&
            regions.map { it.sourceSha256 }.distinct().sorted() == expectedRequest.sourceSha256s.sorted() &&
            ChromeVisualShieldBrowserViewportMapper.map(
                source = carrierCss,
                target = identity.viewport,
                visualViewport = visualViewportCss,
                devicePixelRatio = devicePixelRatio,
                visualViewportScale = visualViewportScale,
                navigationInsets = navigationInsets,
                id = "region-discovery-attested-carrier",
            ) != null

    private fun logValue(value: ChromeVisualShieldRegionDiscoveryObservation): String {
        val result =
            when (val discovery = value.discovery) {
                is ChromeVisualShieldRegionDiscoveryResult.Complete ->
                    "complete sequence=${discovery.discoverySequence} digest=${discovery.regionSetDigest} " +
                        "regions=${discovery.regions.joinToString(";") { "${it.id}:${it.bounds}:${it.visualSignature}" }}"
                is ChromeVisualShieldRegionDiscoveryResult.Unknown ->
                    "unknown reason=${discovery.reason} residual=${discovery.residualEvidence}"
            }
        val decisions =
            value.decisions.joinToString(";") {
                "${it.region.id}:${it.decision.action}:${it.decision.reason}:${it.decision.filterProbability}"
            }
        return "phase=region_discovery_probe scenario=${value.request.scenarioId} " +
            "sourceShas=${value.request.sourceSha256s.joinToString(",")} " +
            "crop=${value.crop.width}x${value.crop.height} cropSha=${value.crop.rgbaSha256} " +
            "result=$result decisions=$decisions authority=${value.authorityResult} " +
            "oracleMatch=${value.oracleMatch} neverRelease=true rawPresented=false " +
            "oracleEvidence=${value.oracleVerification?.logValue() ?: "none"}"
    }
}
