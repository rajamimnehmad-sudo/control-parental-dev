package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.ceil
import kotlin.math.floor

/** DEV-only post-discovery oracle. The planner and regional analyzer never receive it. */
data class ChromeVisualShieldRegionDiscoveryOracle(
    val renderIdentityToken: String,
    val scenarioId: String,
    val renderContract: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val carrierCss: ChromeVisualShieldLabRect,
    val visualViewportCss: ChromeVisualShieldLabRect,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
    val expectComplete: Boolean,
    val regions: List<ChromeVisualShieldRegionDiscoveryOracleRegion>,
) {
    fun isStructurallyValid(): Boolean =
        renderIdentityToken.isNotBlank() &&
            scenarioId.matches(Regex("[a-z0-9-]{1,48}")) &&
            renderContract.matches(Regex("[a-z0-9-]{1,80}")) &&
            canvasWidth > 0 &&
            canvasHeight > 0 &&
            carrierCss.isFinitePositive() &&
            visualViewportCss.isFinitePositive() &&
            visualViewportScale.isFinite() &&
            visualViewportScale > 0.0 &&
            devicePixelRatio.isFinite() &&
            devicePixelRatio > 0.0 &&
            regions.isNotEmpty() &&
            regions.size <= MaximumRegions &&
            regions.all { it.isStructurallyValid(canvasWidth, canvasHeight) }

    private companion object {
        const val MaximumRegions = 8
    }
}

data class ChromeVisualShieldRegionDiscoveryOracleRegion(
    val oracleId: String,
    val sourceSha256: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val drawCanvas: ChromeVisualShieldLabRect,
) {
    internal fun isStructurallyValid(
        canvasWidth: Int,
        canvasHeight: Int,
    ): Boolean =
        oracleId.matches(Regex("[a-z0-9-]{1,48}")) &&
            sourceSha256.matches(Regex("[0-9a-f]{64}")) &&
            sourceWidth > 0 &&
            sourceHeight > 0 &&
            drawCanvas.isFinitePositive() &&
            drawCanvas.left >= 0.0 &&
            drawCanvas.top >= 0.0 &&
            drawCanvas.right <= canvasWidth + GeometryTolerance &&
            drawCanvas.bottom <= canvasHeight + GeometryTolerance

    private companion object {
        const val GeometryTolerance = 0.01
    }
}

internal object ChromeVisualShieldRegionDiscoveryOracleVerifier {
    fun matches(
        identity: ChromeVisualShieldIdentity,
        searchEnvelope: ChromeVisualRegion,
        crop: ChromeVisualShieldCropEvidence,
        request: ChromeVisualShieldRegionDiscoveryProbeRequest,
        oracle: ChromeVisualShieldRegionDiscoveryOracle,
        discovery: ChromeVisualShieldRegionDiscoveryResult,
    ): Boolean {
        if (
            !oracle.isStructurallyValid() ||
            oracle.renderIdentityToken != identity.renderIdentityToken() ||
            oracle.scenarioId != request.scenarioId ||
            oracle.renderContract != request.renderContract ||
            oracle.regions.map { it.sourceSha256 }.distinct().sorted() != request.sourceSha256s.sorted()
        ) {
            return false
        }
        if (!oracle.expectComplete) return discovery is ChromeVisualShieldRegionDiscoveryResult.Unknown
        val complete = discovery as? ChromeVisualShieldRegionDiscoveryResult.Complete ?: return false
        val carrier =
            ChromeVisualShieldBrowserViewportMapper.map(
                source = oracle.carrierCss,
                target = identity.viewport,
                visualViewport = oracle.visualViewportCss,
                devicePixelRatio = oracle.devicePixelRatio,
                visualViewportScale = oracle.visualViewportScale,
                id = "discovery-oracle-carrier",
            ) ?: return false
        val expected =
            oracle.regions.map { region ->
                val global =
                    ChromeVisualRegion(
                        id = region.oracleId,
                        left =
                            floor(
                                carrier.left + carrier.width * region.drawCanvas.left / oracle.canvasWidth,
                            ).toInt(),
                        top =
                            floor(
                                carrier.top + carrier.height * region.drawCanvas.top / oracle.canvasHeight,
                            ).toInt(),
                        right =
                            ceil(
                                carrier.left + carrier.width * region.drawCanvas.right / oracle.canvasWidth,
                            ).toInt(),
                        bottom =
                            ceil(
                                carrier.top + carrier.height * region.drawCanvas.bottom / oracle.canvasHeight,
                            ).toInt(),
                    )
                global.toCrop(searchEnvelope, crop, region.oracleId)
            }.sortedWith(compareBy({ it.top }, { it.left }))
        val actual = complete.regions.map { it.bounds }.sortedWith(compareBy({ it.top }, { it.left }))
        if (actual.size != expected.size) return false
        return actual.zip(expected).all { (candidate, groundTruth) ->
            val intersection = candidate.intersectionArea(groundTruth)
            val groundTruthArea = groundTruth.width * groundTruth.height
            val candidateArea = candidate.width * candidate.height
            groundTruthArea > 0 &&
                intersection.toDouble() / groundTruthArea >= MinimumOracleCoverage &&
                candidateArea <= groundTruthArea * MaximumCandidateAreaRatio
        }
    }

    private fun ChromeVisualRegion.toCrop(
        envelope: ChromeVisualRegion,
        crop: ChromeVisualShieldCropEvidence,
        outputId: String,
    ): ChromeVisualRegion =
        ChromeVisualRegion(
            id = outputId,
            left = ((left - envelope.left).toLong() * crop.width / envelope.width).toInt(),
            top = ((top - envelope.top).toLong() * crop.height / envelope.height).toInt(),
            right = ((right - envelope.left).toLong() * crop.width / envelope.width).toInt(),
            bottom = ((bottom - envelope.top).toLong() * crop.height / envelope.height).toInt(),
        )

    private fun ChromeVisualRegion.intersectionArea(other: ChromeVisualRegion): Int =
        maxOf(0, minOf(right, other.right) - maxOf(left, other.left)) *
            maxOf(0, minOf(bottom, other.bottom) - maxOf(top, other.top))

    private const val MinimumOracleCoverage = 0.98
    private const val MaximumCandidateAreaRatio = 1.5
}

internal object ChromeVisualShieldBrowserViewportMapper {
    fun map(
        source: ChromeVisualShieldLabRect,
        target: ChromeVisualViewport,
        visualViewport: ChromeVisualShieldLabRect,
        devicePixelRatio: Double,
        visualViewportScale: Double,
        id: String,
    ): ChromeVisualRegion? {
        val scale = devicePixelRatio * visualViewportScale
        val projectedViewportWidth = visualViewport.width * scale
        val projectedViewportHeight = visualViewport.height * scale
        if (
            projectedViewportWidth > target.width + PixelTolerance ||
            projectedViewportHeight > target.height + PixelTolerance
        ) {
            return null
        }
        val viewportLeft = target.left + (target.width - projectedViewportWidth) / 2.0
        val viewportTop = target.bottom - projectedViewportHeight
        val mapped =
            ChromeVisualRegion(
                id = id,
                left = floor(viewportLeft + (source.left - visualViewport.left) * scale).toInt(),
                top = floor(viewportTop + (source.top - visualViewport.top) * scale).toInt(),
                right = ceil(viewportLeft + (source.right - visualViewport.left) * scale).toInt(),
                bottom = ceil(viewportTop + (source.bottom - visualViewport.top) * scale).toInt(),
            )
        return mapped.takeIf {
            it.width > 0 &&
                it.height > 0 &&
                it.left >= target.left - PixelTolerance &&
                it.top >= target.top - PixelTolerance &&
                it.right <= target.right + PixelTolerance &&
                it.bottom <= target.bottom + PixelTolerance
        }
    }

    private const val PixelTolerance = 2
}
