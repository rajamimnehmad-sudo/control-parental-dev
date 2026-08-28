package com.contentfilter.feature.accessibility.chromevisual

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds only DEV R2A probe state, its generation barrier, and post-discovery oracle comparison. */
internal class ChromeVisualShieldRegionDiscoveryLab(
    private val discoveryAuthority: ChromeVisualShieldRegionDiscoveryAuthority,
    private val regionSetAuthority: ChromeVisualShieldRegionSetAuthority? = null,
) {
    internal enum class PresentationRecovery {
        RecaptureCurrent,
        ReplaceGeneration,
        ExhaustedFailClosed,
        StaleDropped,
    }

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
    private var presentationRejected = 0L
    private var presentationRecaptures = 0L
    private var presentationReplacements = 0L
    private var presentationExhausted = 0L
    private var lastPresentationReason: String? = null
    private var recapturedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding? = null
    private val signals = mutableMapOf<ChromeVisualShieldRegionDiscoveryRenderBinding, GenerationSignal>()

    @Synchronized
    fun begin(value: ChromeVisualShieldRegionDiscoveryProbeRequest?) {
        completeOutstanding(ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped)
        request = value
        pending = null
        accepted = null
        completed = false
        observation = null
        presentationRejected = 0
        presentationRecaptures = 0
        presentationReplacements = 0
        presentationExhausted = 0
        lastPresentationReason = null
        recapturedBinding = null
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
    fun invalidate(
        outcome: ChromeVisualShieldRegionDiscoveryGenerationOutcome =
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated,
    ) {
        pending?.binding?.let { complete(it, outcome) }
        accepted?.binding?.let { complete(it, outcome) }
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
        recapturedBinding = null
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
    fun hasCurrentBinding(context: ChromeVisualShieldContext): Boolean = accepted?.binding?.matches(context) == true

    @Synchronized
    fun workModeFor(identity: ChromeVisualShieldIdentity): ChromeVisualShieldWorkMode.RegionDiscoveryProbe? {
        val current = accepted ?: return null
        if (!current.binding.matches(identity)) return null
        return ChromeVisualShieldWorkMode.RegionDiscoveryProbe(current.request, current.binding, current.oracle)
    }

    @Synchronized
    fun presentationRejected(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        identity: ChromeVisualShieldIdentity,
        reason: ChromeVisualShieldRegionDiscoveryPresentationRejectReason,
    ): PresentationRecovery {
        val current = accepted
        if (current == null || current.binding != binding || !binding.matches(identity)) {
            return PresentationRecovery.StaleDropped
        }
        presentationRejected += 1
        lastPresentationReason = reason.name
        completed = false
        observation = null
        return if (recapturedBinding != binding) {
            recapturedBinding = binding
            presentationRecaptures += 1
            PresentationRecovery.RecaptureCurrent
        } else if (presentationReplacements < MaximumPresentationReplacements) {
            presentationReplacements += 1
            PresentationRecovery.ReplaceGeneration
        } else {
            accepted = null
            presentationExhausted += 1
            complete(binding, ChromeVisualShieldRegionDiscoveryGenerationOutcome.PresentationFailedClosed)
            PresentationRecovery.ExhaustedFailClosed
        }
    }

    fun awaitGeneration(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        timeoutMillis: Long,
    ): ChromeVisualShieldRegionDiscoveryGenerationOutcome {
        val signal =
            synchronized(this) { signals[binding] }
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
        val mode =
            delivery.work.mode as? ChromeVisualShieldWorkMode.RegionDiscoveryProbe
                ?: return "phase=region_discovery_probe result=unexpected_mode"
        val current = accepted
        if (current == null || current.binding != mode.binding || !mode.binding.matches(delivery.work.identity)) {
            complete(mode.binding, ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated)
            return "phase=region_discovery_probe result=stale_binding neverRelease=true rawPresented=false"
        }
        val authorityResult =
            if (current.request.gateMode == ChromeVisualShieldRegionDiscoveryGateMode.NeverRelease) {
                discoveryAuthority.observe(delivery)
            } else {
                null
            }
        val regionSetAuthorityOutcome =
            if (current.request.gateMode == ChromeVisualShieldRegionDiscoveryGateMode.RegionSetAuthority) {
                regionSetAuthority?.apply(delivery)
                    ?: ChromeVisualShieldRegionSetAuthorityOutcome(
                        result = ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected,
                        reason = "region_set_authority_unavailable",
                        batchIdentity = null,
                        allSafe = false,
                        batchCurrent = false,
                    )
            } else {
                null
            }
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
            authorityResult != ChromeVisualShieldRegionDiscoveryAuthorityResult.IdentityMismatchRejected &&
            regionSetAuthorityOutcome?.result != ChromeVisualShieldRegionSetAuthorityResult.StaleDropped &&
            regionSetAuthorityOutcome?.result != ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected
        observation =
            ChromeVisualShieldRegionDiscoveryObservation(
                request = current.request,
                identity = delivery.work.identity,
                searchEnvelope = delivery.searchEnvelope,
                crop = delivery.cropEvidence,
                discovery = delivery.discovery,
                decisions = delivery.decisions,
                authorityResult = authorityResult,
                regionSetAuthorityOutcome = regionSetAuthorityOutcome,
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
        val regionSet = regionSetAuthority?.snapshot()
        return "regionDiscoveryActive=${request != null} regionDiscoveryCompleted=$completed " +
            "regionDiscoveryResult=$result regionSetDigest=$digest regionOracleMatch=${current?.oracleMatch} " +
            "regionBindingContentEpoch=${binding?.contentEpoch} " +
            "regionBindingViewportEpoch=${binding?.viewportEpoch} " +
            "regionBindingRegionSequence=${binding?.regionSequence} " +
            "regionSetBatchesEvaluated=${regionSet?.batchesEvaluated ?: 0} " +
            "allSafe=${regionSet?.allSafe ?: false} batchCurrent=${regionSet?.batchCurrent ?: false} " +
            "releaseBatchDigest=${regionSet?.releaseBatchDigest ?: "none"} " +
            "authorityAcceptedAtNanos=${regionSet?.authorityAcceptedAtNanos ?: 0} " +
            "regionSetReleaseAtNanos=${regionSet?.releaseAtNanos ?: 0} " +
            "retainedReplayKeys=${regionSet?.retainedReplayKeys ?: 0} " +
            "presentationRejected=$presentationRejected presentationRecaptures=$presentationRecaptures " +
            "presentationReplacements=$presentationReplacements " +
            "presentationExhausted=$presentationExhausted " +
            "lastPresentationReason=${lastPresentationReason ?: "none"}"
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
            presentationProof != null &&
            presentationProof ==
            ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                expectedBinding,
                canvasWidth,
                canvasHeight,
            ) &&
            scenarioId == expectedRequest.scenarioId &&
            renderContract == expectedRequest.renderContract &&
            regions.map { it.sourceSha256 }.sorted() == expectedRequest.sourceSha256s.sorted() &&
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
            "gateMode=${value.request.gateMode} " +
            "sourceShas=${value.request.sourceSha256s.joinToString(",")} " +
            "crop=${value.crop.width}x${value.crop.height} cropSha=${value.crop.rgbaSha256} " +
            "result=$result decisions=$decisions authority=${value.authorityResult} " +
            "regionSetAuthority=${value.regionSetAuthorityOutcome?.result} " +
            "regionSetAuthorityReason=${value.regionSetAuthorityOutcome?.reason} " +
            "allSafe=${value.regionSetAuthorityOutcome?.allSafe ?: false} " +
            "batchCurrent=${value.regionSetAuthorityOutcome?.batchCurrent ?: false} " +
            "releaseBatchDigest=${value.regionSetAuthorityOutcome?.batchIdentity?.regionSetDigest ?: "none"} " +
            "oracleMatch=${value.oracleMatch} " +
            "neverRelease=${value.request.gateMode == ChromeVisualShieldRegionDiscoveryGateMode.NeverRelease} " +
            "rawPresentedBeforeAuthority=false " +
            "oracleEvidence=${value.oracleVerification?.logValue() ?: "none"}"
    }

    private companion object {
        const val MaximumPresentationReplacements = 2L
    }
}
