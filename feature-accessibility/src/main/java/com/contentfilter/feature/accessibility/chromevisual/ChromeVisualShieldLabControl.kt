package com.contentfilter.feature.accessibility.chromevisual

/** Shell-accessible only through the DEV receiver. Production builds have no caller for this gate. */
object ChromeVisualShieldLabControl {
    const val RegionId = "fixture-sentinel-v1"
    const val RegionLeftBasisPoints = 1_500
    const val RegionTopBasisPoints = 2_500
    const val RegionRightBasisPoints = 8_500
    const val RegionBottomBasisPoints = 5_500
    const val RegionDiscoverySearchEnvelopeInsetPixels = 4
    const val FixtureSignature = "compiled:chrome-visual-shield-13b-r:v1"

    @Volatile
    private var endpoint: Endpoint? = null

    fun start(): String = endpoint?.start() ?: Unavailable

    fun stop(): String = endpoint?.stop() ?: Unavailable

    fun release(): String = endpoint?.release() ?: Unavailable

    fun injectStale(): String = endpoint?.injectStale() ?: Unavailable

    fun cancelStress(): String = endpoint?.cancelStress() ?: Unavailable

    fun armAnalyzerFailure(): String = endpoint?.armAnalyzerFailure() ?: Unavailable

    fun renderProbe(
        sampleId: String,
        sourceSha256: String,
        renderContract: String,
    ): String = endpoint?.renderProbe(sampleId, sourceSha256, renderContract) ?: Unavailable

    fun exactDrawOracleProbe(
        sampleId: String,
        sourceSha256: String,
        renderContract: String,
    ): String = endpoint?.exactDrawOracleProbe(sampleId, sourceSha256, renderContract) ?: Unavailable

    fun regionDiscoveryProbe(
        scenarioId: String,
        sourceSha256s: List<String>,
        renderContract: String,
    ): String = endpoint?.regionDiscoveryProbe(scenarioId, sourceSha256s, renderContract, false) ?: Unavailable

    fun regionSetAuthorityProbe(
        scenarioId: String,
        sourceSha256s: List<String>,
        renderContract: String,
    ): String = endpoint?.regionDiscoveryProbe(scenarioId, sourceSha256s, renderContract, true) ?: Unavailable

    fun currentRenderIdentityToken(): String? = endpoint?.currentRenderIdentityToken()

    fun beginFixtureRender(): String? = endpoint?.beginFixtureRender()

    fun currentRegionDiscoveryGeneration(): ChromeVisualShieldRegionDiscoveryNativeGeneration? =
        endpoint?.currentRegionDiscoveryGeneration()

    fun beginRegionDiscoveryFixtureRender(
        renderGeometryKeyDigest: String,
    ): ChromeVisualShieldRegionDiscoveryRenderBinding? =
        endpoint?.beginRegionDiscoveryFixtureRender(renderGeometryKeyDigest)

    fun renderAttested(
        renderIdentityToken: String,
        exactDrawOracle: ChromeVisualShieldExactDrawOracle? = null,
        regionDiscoveryOracle: ChromeVisualShieldRegionDiscoveryOracle? = null,
        regionDiscoveryBinding: ChromeVisualShieldRegionDiscoveryRenderBinding? = null,
    ): String =
        endpoint?.renderAttested(
            renderIdentityToken,
            exactDrawOracle,
            regionDiscoveryOracle,
            regionDiscoveryBinding,
        ) ?: Unavailable

    fun awaitRegionDiscoveryGeneration(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        timeoutMillis: Long,
    ): ChromeVisualShieldRegionDiscoveryGenerationOutcome =
        endpoint?.awaitRegionDiscoveryGeneration(binding, timeoutMillis)
            ?: ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped

    fun status(): String = endpoint?.status() ?: Unavailable

    internal fun bind(value: Endpoint) {
        endpoint = value
    }

    internal fun unbind(value: Endpoint) {
        if (endpoint === value) endpoint = null
    }

    internal interface Endpoint {
        fun start(): String

        fun stop(): String

        fun release(): String

        fun injectStale(): String

        fun cancelStress(): String

        fun armAnalyzerFailure(): String

        fun renderProbe(
            sampleId: String,
            sourceSha256: String,
            renderContract: String,
        ): String

        fun exactDrawOracleProbe(
            sampleId: String,
            sourceSha256: String,
            renderContract: String,
        ): String

        fun regionDiscoveryProbe(
            scenarioId: String,
            sourceSha256s: List<String>,
            renderContract: String,
            regionSetAuthority: Boolean,
        ): String

        fun currentRenderIdentityToken(): String?

        fun beginFixtureRender(): String?

        fun currentRegionDiscoveryGeneration(): ChromeVisualShieldRegionDiscoveryNativeGeneration?

        fun beginRegionDiscoveryFixtureRender(
            renderGeometryKeyDigest: String,
        ): ChromeVisualShieldRegionDiscoveryRenderBinding?

        fun renderAttested(
            renderIdentityToken: String,
            exactDrawOracle: ChromeVisualShieldExactDrawOracle?,
            regionDiscoveryOracle: ChromeVisualShieldRegionDiscoveryOracle?,
            regionDiscoveryBinding: ChromeVisualShieldRegionDiscoveryRenderBinding?,
        ): String

        fun awaitRegionDiscoveryGeneration(
            binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
            timeoutMillis: Long,
        ): ChromeVisualShieldRegionDiscoveryGenerationOutcome

        fun status(): String
    }

    private const val Unavailable = "result=unavailable"
}
