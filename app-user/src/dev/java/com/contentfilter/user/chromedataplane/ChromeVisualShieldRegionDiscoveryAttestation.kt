package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabRect
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryOracle
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryOracleRegion
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryPresentationMarkerContract
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryPresentationProof
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryRenderBinding
import kotlin.math.abs

internal data class ChromeVisualShieldRegionDiscoveryAttestation(
    val scenario: ChromeVisualShieldRegionDiscoveryScenario,
    val binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val carrierCss: ChromeVisualShieldLabRect,
    val visualViewportCss: ChromeVisualShieldLabRect,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
    val presentationProof: ChromeVisualShieldRegionDiscoveryPresentationProof,
    val renderTimeline: String,
    val draws: List<ChromeVisualShieldRegionDiscoveryDraw>,
) {
    fun oracle(): ChromeVisualShieldRegionDiscoveryOracle =
        ChromeVisualShieldRegionDiscoveryOracle(
            renderIdentityToken = binding.renderIdentityToken,
            scenarioId = scenario.wireName,
            renderContract = ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            carrierCss = carrierCss,
            visualViewportCss = visualViewportCss,
            visualViewportScale = visualViewportScale,
            devicePixelRatio = devicePixelRatio,
            presentationProof = presentationProof,
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
        expectedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    ): String {
        val fields = body.split('|')
        if (fields.size != FieldCount) return invalid(scenario)
        if (
            fields[0] != scenario.wireName ||
            fields[1] != ChromeVisualShieldRegionDiscoveryLayoutContract.Version ||
            fields[2] != expectedBinding.renderIdentityToken ||
            fields[3] != expectedBinding.renderGeometryKeyDigest ||
            fields[4].toLongOrNull() != expectedBinding.protectionSessionId ||
            fields[5].toIntOrNull() != expectedBinding.windowId ||
            fields[6].toLongOrNull() != expectedBinding.contentEpoch ||
            fields[7].toLongOrNull() != expectedBinding.viewportEpoch ||
            fields[8].toLongOrNull() != expectedBinding.regionSequence ||
            fields[21].toBooleanStrictOrNull() != scenario.expectComplete
        ) {
            return mismatch(scenario)
        }
        val canvasWidth = fields[9].toIntOrNull() ?: return invalid(scenario)
        val canvasHeight = fields[10].toIntOrNull() ?: return invalid(scenario)
        val carrier = rect(fields, 11) ?: return invalid(scenario)
        val viewport = rect(fields, 15) ?: return invalid(scenario)
        val viewportScale = fields[19].toDoubleOrNull() ?: return invalid(scenario)
        val dpr = fields[20].toDoubleOrNull() ?: return invalid(scenario)
        val suppliedProof =
            ChromeVisualShieldRegionDiscoveryPresentationProof(
                pattern = fields[22],
                markerCanvas =
                    ChromeVisualShieldLabRect(
                        fields[23].toDoubleOrNull() ?: return invalid(scenario),
                        fields[24].toDoubleOrNull() ?: return invalid(scenario),
                        fields[22].length * (fields[25].toIntOrNull() ?: return invalid(scenario)).toDouble(),
                        fields[26].toDoubleOrNull() ?: return invalid(scenario),
                    ),
                cellWidth = fields[25].toIntOrNull() ?: return invalid(scenario),
            )
        val expectedProof =
            ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                expectedBinding,
                canvasWidth,
                canvasHeight,
            ) ?: return invalid(scenario)
        if (suppliedProof != expectedProof) {
            return "result=region_attestation_presentation_mismatch scenario=${scenario.wireName}"
        }
        val renderTimeline = fields[27]
        if (renderTimeline != ExpectedRenderTimeline) return invalid(scenario)
        val drawFields = fields[28].split(';')
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
                expectedBinding,
                canvasWidth,
                canvasHeight,
                carrier,
                viewport,
                viewportScale,
                dpr,
                suppliedProof,
                renderTimeline,
                observed,
            )
        if (!attestation.oracle().isStructurallyValid()) return invalid(scenario)
        ready[scenario] = attestation
        return "result=region_render_attested scenario=${scenario.wireName} draws=${observed.size} " +
            "timeline=$renderTimeline presentationProofDeclared=true"
    }

    @Synchronized
    fun peek(
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
        expectedBinding: ChromeVisualShieldRegionDiscoveryRenderBinding,
    ): ChromeVisualShieldRegionDiscoveryAttestation? = ready[scenario]?.takeIf { it.binding == expectedBinding }

    @Synchronized
    fun clear(scenario: ChromeVisualShieldRegionDiscoveryScenario) {
        ready.remove(scenario)
    }

    @Synchronized
    fun clear() {
        ready.clear()
        ChromeVisualShieldRegionDiscoveryHandshakeStore.clear()
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

    private const val FieldCount = 29
    private const val DrawFieldCount = 8
    private const val GeometryTolerance = 0.02
    private const val ExpectedRenderTimeline =
        "draw_started,draw_completed,presentation_marker_drawn,raf_boundary_1,raf_boundary_2,attestation_submitted"
}
