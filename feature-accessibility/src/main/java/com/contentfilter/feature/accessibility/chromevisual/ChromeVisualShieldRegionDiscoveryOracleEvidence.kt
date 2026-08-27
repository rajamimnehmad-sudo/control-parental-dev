package com.contentfilter.feature.accessibility.chromevisual

import java.util.Locale
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
    data class Condition(
        val name: String,
        val passed: Boolean,
        val detail: String,
    )

    data class RegionComparison(
        val expected: ChromeVisualRegion,
        val candidate: ChromeVisualRegion,
        val intersection: Int,
        val oracleCoverage: Double,
        val candidateAreaRatio: Double,
        val insideSearchFraction: Double,
        val deltaX: Double,
        val deltaY: Double,
        val scaleX: Double,
        val scaleY: Double,
    ) {
        val coveragePass: Boolean = oracleCoverage >= MinimumOracleCoverage
        val candidateAreaPass: Boolean = candidateAreaRatio <= MaximumCandidateAreaRatio

        fun logValue(): String =
            "expected=${expected.compact()},candidate=${candidate.compact()},intersection=$intersection," +
                "oracleCoverage=${oracleCoverage.fixed()},candidateAreaRatio=${candidateAreaRatio.fixed()}," +
                "insideSearchFraction=${insideSearchFraction.fixed()},deltaX=${deltaX.fixed()}," +
                "deltaY=${deltaY.fixed()},scaleX=${scaleX.fixed()},scaleY=${scaleY.fixed()}," +
                "coveragePass=$coveragePass,candidateAreaPass=$candidateAreaPass"
    }

    data class Verification(
        val matches: Boolean,
        val searchEnvelope: ChromeVisualRegion,
        val mappedCarrier: ChromeVisualRegion?,
        val conditions: List<Condition>,
        val comparisons: List<RegionComparison>,
    ) {
        fun logValue(): String {
            val conditionValue =
                conditions.joinToString(";") { "${it.name}:${it.passed}:${it.detail}" }
            val comparisonValue =
                comparisons.mapIndexed { index, value -> "r${index + 1}{${value.logValue()}}" }
                    .joinToString(";")
                    .ifEmpty { "none" }
            return "oracleMatch=$matches searchEnvelope=${searchEnvelope.compact()} " +
                "carrier=${mappedCarrier?.compact() ?: "none"} conditions=$conditionValue " +
                "comparisons=$comparisonValue"
        }
    }

    fun matches(
        identity: ChromeVisualShieldIdentity,
        searchEnvelope: ChromeVisualRegion,
        crop: ChromeVisualShieldCropEvidence,
        request: ChromeVisualShieldRegionDiscoveryProbeRequest,
        oracle: ChromeVisualShieldRegionDiscoveryOracle,
        discovery: ChromeVisualShieldRegionDiscoveryResult,
    ): Boolean = verify(identity, searchEnvelope, crop, request, oracle, discovery).matches

    fun verify(
        identity: ChromeVisualShieldIdentity,
        searchEnvelope: ChromeVisualRegion,
        crop: ChromeVisualShieldCropEvidence,
        request: ChromeVisualShieldRegionDiscoveryProbeRequest,
        oracle: ChromeVisualShieldRegionDiscoveryOracle,
        discovery: ChromeVisualShieldRegionDiscoveryResult,
    ): Verification {
        val conditions = mutableListOf<Condition>()

        fun record(
            name: String,
            passed: Boolean,
            detail: String,
        ) {
            conditions += Condition(name, passed, detail)
        }

        val structural = oracle.isStructurallyValid()
        record("structural", structural, "valid=$structural")
        val identityMatches = oracle.renderIdentityToken == identity.renderIdentityToken()
        record("identity", identityMatches, "tokenMatch=$identityMatches")
        val scenarioMatches = oracle.scenarioId == request.scenarioId
        record("scenario", scenarioMatches, "expected=${request.scenarioId},actual=${oracle.scenarioId}")
        val renderContractMatches = oracle.renderContract == request.renderContract
        record(
            "renderContract",
            renderContractMatches,
            "expected=${request.renderContract},actual=${oracle.renderContract}",
        )
        val sourceMatches =
            oracle.regions.map { it.sourceSha256 }.distinct().sorted() == request.sourceSha256s.sorted()
        record("sources", sourceMatches, "match=$sourceMatches")
        val commonConditions = structural && identityMatches && scenarioMatches && renderContractMatches && sourceMatches
        if (!oracle.expectComplete) {
            val expectedUnknown = discovery is ChromeVisualShieldRegionDiscoveryResult.Unknown
            record("resultType", expectedUnknown, "expected=unknown,actual=${discovery.typeName()}")
            return Verification(
                matches = commonConditions && expectedUnknown,
                searchEnvelope = searchEnvelope,
                mappedCarrier = null,
                conditions = conditions,
                comparisons = emptyList(),
            )
        }
        val complete = discovery as? ChromeVisualShieldRegionDiscoveryResult.Complete
        record("resultType", complete != null, "expected=complete,actual=${discovery.typeName()}")
        val carrier =
            if (structural) {
                ChromeVisualShieldBrowserViewportMapper.map(
                    source = oracle.carrierCss,
                    target = identity.viewport,
                    visualViewport = oracle.visualViewportCss,
                    devicePixelRatio = oracle.devicePixelRatio,
                    visualViewportScale = oracle.visualViewportScale,
                    id = "discovery-oracle-carrier",
                )
            } else {
                null
            }
        record("carrierMapping", carrier != null, "carrier=${carrier?.compact() ?: "none"}")
        if (complete == null || carrier == null) {
            return Verification(false, searchEnvelope, carrier, conditions, emptyList())
        }
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
        val regionCountMatches = actual.size == expected.size
        record("regionCount", regionCountMatches, "expected=${expected.size},actual=${actual.size}")
        val cropBounds = ChromeVisualRegion("crop", 0, 0, crop.width, crop.height)
        val comparisons =
            actual.zip(expected).map { (candidate, groundTruth) ->
                val intersection = candidate.intersectionArea(groundTruth)
                val groundTruthArea = groundTruth.width * groundTruth.height
                val candidateArea = candidate.width * candidate.height
                RegionComparison(
                    expected = groundTruth,
                    candidate = candidate,
                    intersection = intersection,
                    oracleCoverage = intersection.toDouble() / groundTruthArea.coerceAtLeast(1),
                    candidateAreaRatio = candidateArea.toDouble() / groundTruthArea.coerceAtLeast(1),
                    insideSearchFraction =
                        candidate.intersectionArea(cropBounds).toDouble() / candidateArea.coerceAtLeast(1),
                    deltaX = candidate.centerX - groundTruth.centerX,
                    deltaY = candidate.centerY - groundTruth.centerY,
                    scaleX = candidate.width.toDouble() / groundTruth.width.coerceAtLeast(1),
                    scaleY = candidate.height.toDouble() / groundTruth.height.coerceAtLeast(1),
                )
            }
        comparisons.forEachIndexed { index, comparison ->
            record("region${index + 1}Coverage", comparison.coveragePass, comparison.logValue())
            record("region${index + 1}Area", comparison.candidateAreaPass, comparison.logValue())
        }
        return Verification(
            matches =
                commonConditions &&
                    regionCountMatches &&
                    comparisons.size == expected.size &&
                    comparisons.all { it.coveragePass && it.candidateAreaPass },
            searchEnvelope = searchEnvelope,
            mappedCarrier = carrier,
            conditions = conditions,
            comparisons = comparisons,
        )
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

    private fun ChromeVisualShieldRegionDiscoveryResult.typeName(): String =
        when (this) {
            is ChromeVisualShieldRegionDiscoveryResult.Complete -> "complete"
            is ChromeVisualShieldRegionDiscoveryResult.Unknown -> "unknown"
        }

    private fun ChromeVisualRegion.compact(): String = "$left,$top,$right,$bottom"

    private val ChromeVisualRegion.centerX: Double get() = (left + right) / 2.0
    private val ChromeVisualRegion.centerY: Double get() = (top + bottom) / 2.0

    private fun Double.fixed(): String = String.format(Locale.US, "%.6f", this)

    internal const val MinimumOracleCoverage = 0.98
    internal const val MaximumCandidateAreaRatio = 1.5
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
