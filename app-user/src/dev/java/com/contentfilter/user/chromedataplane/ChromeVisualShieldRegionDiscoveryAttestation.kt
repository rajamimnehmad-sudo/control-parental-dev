package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabRect
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryOracle
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryOracleRegion
import kotlin.math.abs

internal data class ChromeVisualShieldRegionDiscoveryAttestation(
    val scenario: ChromeVisualShieldRegionDiscoveryScenario,
    val renderIdentityToken: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val carrierCss: ChromeVisualShieldLabRect,
    val visualViewportCss: ChromeVisualShieldLabRect,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
    val draws: List<ChromeVisualShieldRegionDiscoveryDraw>,
) {
    fun oracle(): ChromeVisualShieldRegionDiscoveryOracle =
        ChromeVisualShieldRegionDiscoveryOracle(
            renderIdentityToken = renderIdentityToken,
            scenarioId = scenario.wireName,
            renderContract = ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            carrierCss = carrierCss,
            visualViewportCss = visualViewportCss,
            visualViewportScale = visualViewportScale,
            devicePixelRatio = devicePixelRatio,
            expectComplete = scenario.expectComplete,
            regions =
                draws.mapIndexed { index, draw ->
                    ChromeVisualShieldRegionDiscoveryOracleRegion(
                        oracleId = "oracle-${index + 1}-${draw.sample.wireName}",
                        sourceSha256 = draw.sample.expectedSha256,
                        sourceWidth = draw.sourceWidth,
                        sourceHeight = draw.sourceHeight,
                        drawCanvas =
                            ChromeVisualShieldLabRect(
                                draw.geometry.left,
                                draw.geometry.top,
                                draw.geometry.width,
                                draw.geometry.height,
                            ),
                    )
                },
        )
}

internal object ChromeVisualShieldRegionDiscoveryAttestationStore {
    private val ready =
        mutableMapOf<ChromeVisualShieldRegionDiscoveryScenario, ChromeVisualShieldRegionDiscoveryAttestation>()

    @Synchronized
    fun record(
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
        body: String,
        expectedRenderIdentityToken: String,
    ): String {
        val fields = body.split('|')
        if (fields.size != FieldCount) return invalid(scenario)
        if (
            fields[0] != scenario.wireName ||
            fields[1] != ChromeVisualShieldRegionDiscoveryLayoutContract.Version ||
            fields[2] != expectedRenderIdentityToken ||
            fields[15].toBooleanStrictOrNull() != scenario.expectComplete
        ) {
            return mismatch(scenario)
        }
        val canvasWidth = fields[3].toIntOrNull() ?: return invalid(scenario)
        val canvasHeight = fields[4].toIntOrNull() ?: return invalid(scenario)
        val carrier = rect(fields, 5) ?: return invalid(scenario)
        val viewport = rect(fields, 9) ?: return invalid(scenario)
        val viewportScale = fields[13].toDoubleOrNull() ?: return invalid(scenario)
        val dpr = fields[14].toDoubleOrNull() ?: return invalid(scenario)
        val drawFields = fields[16].split(';')
        if (drawFields.size != scenario.samples.size) return mismatch(scenario)
        val observed =
            drawFields.mapIndexed { index, encoded ->
                val values = encoded.split(',')
                if (values.size != DrawFieldCount) return invalid(scenario)
                val sample = scenario.samples[index]
                if (values[0] != sample.wireName || values[1] != sample.expectedSha256) return mismatch(scenario)
                ChromeVisualShieldRegionDiscoveryDraw(
                    sample = sample,
                    sourceWidth = values[2].toIntOrNull() ?: return invalid(scenario),
                    sourceHeight = values[3].toIntOrNull() ?: return invalid(scenario),
                    geometry =
                        ChromeVisualShieldContainGeometry(
                            values[4].toDoubleOrNull() ?: return invalid(scenario),
                            values[5].toDoubleOrNull() ?: return invalid(scenario),
                            values[6].toDoubleOrNull() ?: return invalid(scenario),
                            values[7].toDoubleOrNull() ?: return invalid(scenario),
                        ),
                )
            }
        val expected =
            ChromeVisualShieldRegionDiscoveryLayoutContract.geometry(
                scenario,
                observed.map { it.sourceWidth to it.sourceHeight },
                canvasWidth,
                canvasHeight,
            ) ?: return invalid(scenario)
        if (!observed.map { it.geometry }.zip(expected).all { (actual, target) -> actual.matches(target) }) {
            return "result=region_attestation_geometry_mismatch scenario=${scenario.wireName}"
        }
        val attestation =
            ChromeVisualShieldRegionDiscoveryAttestation(
                scenario,
                expectedRenderIdentityToken,
                canvasWidth,
                canvasHeight,
                carrier,
                viewport,
                viewportScale,
                dpr,
                observed,
            )
        if (!attestation.oracle().isStructurallyValid()) return invalid(scenario)
        ready[scenario] = attestation
        return "result=region_render_attested scenario=${scenario.wireName} draws=${observed.size}"
    }

    @Synchronized
    fun peek(
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
        expectedRenderIdentityToken: String,
    ): ChromeVisualShieldRegionDiscoveryAttestation? =
        ready[scenario]?.takeIf { it.renderIdentityToken == expectedRenderIdentityToken }

    @Synchronized
    fun clear(scenario: ChromeVisualShieldRegionDiscoveryScenario) {
        ready.remove(scenario)
    }

    @Synchronized
    fun clear() {
        ready.clear()
    }

    private fun rect(
        fields: List<String>,
        offset: Int,
    ): ChromeVisualShieldLabRect? {
        val values = (offset until offset + 4).map { fields[it].toDoubleOrNull() ?: return null }
        return ChromeVisualShieldLabRect(values[0], values[1], values[2], values[3])
    }

    private fun ChromeVisualShieldContainGeometry.matches(expected: ChromeVisualShieldContainGeometry): Boolean =
        listOf(left, top, width, height).all(Double::isFinite) &&
            abs(left - expected.left) <= GeometryTolerance &&
            abs(top - expected.top) <= GeometryTolerance &&
            abs(width - expected.width) <= GeometryTolerance &&
            abs(height - expected.height) <= GeometryTolerance

    private fun invalid(scenario: ChromeVisualShieldRegionDiscoveryScenario) =
        "result=region_attestation_invalid scenario=${scenario.wireName}"

    private fun mismatch(scenario: ChromeVisualShieldRegionDiscoveryScenario) =
        "result=region_attestation_identity_mismatch scenario=${scenario.wireName}"

    private const val FieldCount = 17
    private const val DrawFieldCount = 8
    private const val GeometryTolerance = 0.02
}
