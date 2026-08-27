package com.contentfilter.feature.accessibility.chromevisual

/** Holds only DEV R2A probe state and post-discovery oracle comparison. */
internal class ChromeVisualShieldRegionDiscoveryLab(
    private val authority: ChromeVisualShieldRegionDiscoveryAuthority,
) {
    private var request: ChromeVisualShieldRegionDiscoveryProbeRequest? = null
    private var oracle: ChromeVisualShieldRegionDiscoveryOracle? = null
    private var completed = false
    private var observation: ChromeVisualShieldRegionDiscoveryObservation? = null

    @Synchronized
    fun begin(value: ChromeVisualShieldRegionDiscoveryProbeRequest?) {
        request = value
        oracle = null
        completed = false
        if (value != null) observation = null
    }

    @Synchronized
    fun clear() {
        request = null
        oracle = null
        completed = false
    }

    @Synchronized
    fun invalidate() {
        oracle = null
        completed = false
        observation = null
    }

    @Synchronized
    fun isActive(): Boolean = request != null

    @Synchronized
    fun isCompleted(): Boolean = completed

    @Synchronized
    fun workModeOrNull(): ChromeVisualShieldWorkMode.RegionDiscoveryProbe? =
        request?.let(ChromeVisualShieldWorkMode::RegionDiscoveryProbe)

    @Synchronized
    fun recordOracle(
        renderIdentityToken: String,
        identity: ChromeVisualShieldIdentity,
        candidate: ChromeVisualShieldRegionDiscoveryOracle?,
    ): Boolean {
        val currentRequest = request ?: return candidate == null
        val value = candidate ?: return false
        if (
            !value.isStructurallyValid() ||
            value.renderIdentityToken != renderIdentityToken ||
            value.scenarioId != currentRequest.scenarioId ||
            value.renderContract != currentRequest.renderContract ||
            value.regions.map { it.sourceSha256 }.distinct().sorted() != currentRequest.sourceSha256s.sorted() ||
            ChromeVisualShieldBrowserViewportMapper.map(
                source = value.carrierCss,
                target = identity.viewport,
                visualViewport = value.visualViewportCss,
                devicePixelRatio = value.devicePixelRatio,
                visualViewportScale = value.visualViewportScale,
                id = "region-discovery-attested-carrier",
            ) == null
        ) {
            return false
        }
        oracle = value
        return true
    }

    @Synchronized
    fun deliver(delivery: ChromeVisualShieldRegionDiscoveryDelivery): String {
        val currentRequest =
            (delivery.work.mode as? ChromeVisualShieldWorkMode.RegionDiscoveryProbe)?.request
                ?: return "phase=region_discovery_probe result=unexpected_mode"
        val authorityResult = authority.observe(delivery)
        val oracleMatch =
            oracle?.let { currentOracle ->
                ChromeVisualShieldRegionDiscoveryOracleVerifier.matches(
                    identity = delivery.work.identity,
                    searchEnvelope = delivery.searchEnvelope,
                    crop = delivery.cropEvidence,
                    request = currentRequest,
                    oracle = currentOracle,
                    discovery = delivery.discovery,
                )
            }
        completed =
            oracleMatch == true &&
            authorityResult != ChromeVisualShieldRegionDiscoveryAuthorityResult.StaleDropped &&
            authorityResult != ChromeVisualShieldRegionDiscoveryAuthorityResult.IdentityMismatchRejected
        observation =
            ChromeVisualShieldRegionDiscoveryObservation(
                request = currentRequest,
                identity = delivery.work.identity,
                searchEnvelope = delivery.searchEnvelope,
                crop = delivery.cropEvidence,
                discovery = delivery.discovery,
                decisions = delivery.decisions,
                authorityResult = authorityResult,
                oracleMatch = oracleMatch,
            )
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
        return "regionDiscoveryActive=${request != null} regionDiscoveryCompleted=$completed " +
            "regionDiscoveryResult=$result regionSetDigest=$digest regionOracleMatch=${current?.oracleMatch}"
    }

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
            "oracleMatch=${value.oracleMatch} neverRelease=true rawPresented=false"
    }
}
