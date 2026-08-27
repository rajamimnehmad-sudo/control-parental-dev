package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldExactDrawOracle
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabRect
import kotlin.math.abs

internal data class ChromeVisualShieldRenderAttestation(
    val sample: ChromeVisualShieldFixtureSample,
    val renderIdentityToken: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val draw: ChromeVisualShieldContainGeometry,
    val carrierCss: ChromeVisualShieldLabRect,
    val visualViewportCss: ChromeVisualShieldLabRect,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
) {
    fun exactDrawOracle(): ChromeVisualShieldExactDrawOracle =
        ChromeVisualShieldExactDrawOracle(
            renderIdentityToken = renderIdentityToken,
            sourceSha256 = sample.expectedSha256,
            renderContract = ChromeVisualShieldContainContract.Version,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            carrierCss = carrierCss,
            visualViewportCss = visualViewportCss,
            visualViewportScale = visualViewportScale,
            devicePixelRatio = devicePixelRatio,
            drawCanvas = ChromeVisualShieldLabRect(draw.left, draw.top, draw.width, draw.height),
        )
}

/** One-shot browser-to-fixture proof that the expected source was drawn with the contain contract. */
internal object ChromeVisualShieldRenderAttestationStore {
    private val ready = mutableMapOf<ChromeVisualShieldFixtureSample, ChromeVisualShieldRenderAttestation>()

    @Synchronized
    fun record(
        sample: ChromeVisualShieldFixtureSample,
        body: String,
        expectedRenderIdentityToken: String,
    ): String {
        val fields = body.split('|')
        if (fields.size != FieldCount) return "result=render_attestation_invalid sample=${sample.wireName}"
        if (
            fields[0] != sample.expectedSha256 ||
            fields[1] != ChromeVisualShieldContainContract.Version ||
            fields[2] != expectedRenderIdentityToken
        ) {
            return "result=render_attestation_identity_mismatch sample=${sample.wireName}"
        }
        val sourceWidth = fields[3].toIntOrNull() ?: return invalid(sample)
        val sourceHeight = fields[4].toIntOrNull() ?: return invalid(sample)
        val canvasWidth = fields[5].toIntOrNull() ?: return invalid(sample)
        val canvasHeight = fields[6].toIntOrNull() ?: return invalid(sample)
        val observed =
            ChromeVisualShieldContainGeometry(
                left = fields[7].toDoubleOrNull() ?: return invalid(sample),
                top = fields[8].toDoubleOrNull() ?: return invalid(sample),
                width = fields[9].toDoubleOrNull() ?: return invalid(sample),
                height = fields[10].toDoubleOrNull() ?: return invalid(sample),
            )
        val carrierCss =
            ChromeVisualShieldLabRect(
                left = fields[11].toDoubleOrNull() ?: return invalid(sample),
                top = fields[12].toDoubleOrNull() ?: return invalid(sample),
                width = fields[13].toDoubleOrNull() ?: return invalid(sample),
                height = fields[14].toDoubleOrNull() ?: return invalid(sample),
            )
        val visualViewportCss =
            ChromeVisualShieldLabRect(
                left = fields[15].toDoubleOrNull() ?: return invalid(sample),
                top = fields[16].toDoubleOrNull() ?: return invalid(sample),
                width = fields[17].toDoubleOrNull() ?: return invalid(sample),
                height = fields[18].toDoubleOrNull() ?: return invalid(sample),
            )
        val visualViewportScale = fields[19].toDoubleOrNull() ?: return invalid(sample)
        val devicePixelRatio = fields[20].toDoubleOrNull() ?: return invalid(sample)
        val expected =
            ChromeVisualShieldContainContract.geometry(
                sourceWidth,
                sourceHeight,
                canvasWidth,
                canvasHeight,
            ) ?: return invalid(sample)
        if (!observed.matches(expected)) return "result=render_attestation_geometry_mismatch sample=${sample.wireName}"
        val attestation =
            ChromeVisualShieldRenderAttestation(
                sample,
                expectedRenderIdentityToken,
                sourceWidth,
                sourceHeight,
                canvasWidth,
                canvasHeight,
                observed,
                carrierCss,
                visualViewportCss,
                visualViewportScale,
                devicePixelRatio,
            )
        if (!attestation.exactDrawOracle().isStructurallyValid()) {
            return "result=render_attestation_mapping_invalid sample=${sample.wireName}"
        }
        ready[sample] = attestation
        return "result=render_attested sample=${sample.wireName} source=${sourceWidth}x$sourceHeight " +
            "canvas=${canvasWidth}x$canvasHeight draw=${observed.asLogValue()}"
    }

    @Synchronized
    fun consume(
        sample: ChromeVisualShieldFixtureSample,
        expectedRenderIdentityToken: String,
    ): ChromeVisualShieldRenderAttestation? =
        ready.remove(sample)?.takeIf { it.renderIdentityToken == expectedRenderIdentityToken }

    @Synchronized
    fun peek(
        sample: ChromeVisualShieldFixtureSample,
        expectedRenderIdentityToken: String,
    ): ChromeVisualShieldRenderAttestation? =
        ready[sample]?.takeIf { it.renderIdentityToken == expectedRenderIdentityToken }

    @Synchronized
    fun clear(sample: ChromeVisualShieldFixtureSample) {
        ready.remove(sample)
    }

    @Synchronized
    fun clear() {
        ready.clear()
    }

    private fun ChromeVisualShieldContainGeometry.matches(expected: ChromeVisualShieldContainGeometry): Boolean =
        listOf(left, top, width, height).all(Double::isFinite) &&
            abs(left - expected.left) <= GeometryTolerance &&
            abs(top - expected.top) <= GeometryTolerance &&
            abs(width - expected.width) <= GeometryTolerance &&
            abs(height - expected.height) <= GeometryTolerance

    private fun ChromeVisualShieldContainGeometry.asLogValue(): String = "$left,$top,$width,$height"

    private fun invalid(sample: ChromeVisualShieldFixtureSample) =
        "result=render_attestation_invalid sample=${sample.wireName}"

    private const val FieldCount = 21
    private const val GeometryTolerance = 0.01
}
