package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class ChromeVisualShieldLabRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height

    internal fun isFinitePositive(): Boolean =
        listOf(left, top, width, height).all(Double::isFinite) && width > 0.0 && height > 0.0
}

/** DEV-only oracle supplied by the signed fixture. Region discovery never receives this value. */
data class ChromeVisualShieldExactDrawOracle(
    val renderIdentityToken: String,
    val sourceSha256: String,
    val renderContract: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val carrierCss: ChromeVisualShieldLabRect,
    val visualViewportCss: ChromeVisualShieldLabRect,
    val visualViewportScale: Double,
    val devicePixelRatio: Double,
    val drawCanvas: ChromeVisualShieldLabRect,
) {
    fun isStructurallyValid(): Boolean =
        renderIdentityToken.isNotBlank() &&
            sourceSha256.matches(Regex("[0-9a-f]{64}")) &&
            renderContract.matches(Regex("[a-z0-9-]{1,80}")) &&
            sourceWidth > 0 &&
            sourceHeight > 0 &&
            canvasWidth > 0 &&
            canvasHeight > 0 &&
            carrierCss.isFinitePositive() &&
            visualViewportCss.isFinitePositive() &&
            visualViewportScale.isFinite() &&
            visualViewportScale > 0.0 &&
            devicePixelRatio.isFinite() &&
            devicePixelRatio > 0.0 &&
            drawCanvas.isFinitePositive() &&
            drawCanvas.left >= 0.0 &&
            drawCanvas.top >= 0.0 &&
            drawCanvas.right <= canvasWidth + GeometryTolerance &&
            drawCanvas.bottom <= canvasHeight + GeometryTolerance &&
            carrierCss.left >= visualViewportCss.left - GeometryTolerance &&
            carrierCss.top >= visualViewportCss.top - GeometryTolerance &&
            carrierCss.right <= visualViewportCss.right + GeometryTolerance &&
            carrierCss.bottom <= visualViewportCss.bottom + GeometryTolerance &&
            abs(canvasWidth - carrierCss.width * devicePixelRatio) <= PixelTolerance &&
            abs(canvasHeight - carrierCss.height * devicePixelRatio) <= PixelTolerance

    private companion object {
        const val GeometryTolerance = 0.01
        const val PixelTolerance = 2.0
    }
}

/** Maps the exact signed draw rectangle into the current captured-frame coordinate contract. */
internal object ChromeVisualShieldExactDrawOracleMapper {
    fun resolve(
        identity: ChromeVisualShieldIdentity,
        request: ChromeVisualShieldRenderProbeRequest,
    ): ChromeVisualRegion? {
        val oracle = request.exactDrawOracle ?: return null
        if (
            !oracle.isStructurallyValid() ||
            oracle.renderIdentityToken != identity.renderIdentityToken() ||
            oracle.sourceSha256 != request.sourceSha256 ||
            oracle.renderContract != request.renderContract
        ) {
            return null
        }
        val mappedCarrier =
            ChromeVisualShieldBrowserViewportMapper.map(
                source = oracle.carrierCss,
                target = identity.viewport,
                visualViewport = oracle.visualViewportCss,
                devicePixelRatio = oracle.devicePixelRatio,
                visualViewportScale = oracle.visualViewportScale,
                id = "oracle-carrier",
            ) ?: return null

        val left = mappedCarrier.left + mappedCarrier.width * oracle.drawCanvas.left / oracle.canvasWidth
        val top = mappedCarrier.top + mappedCarrier.height * oracle.drawCanvas.top / oracle.canvasHeight
        val right = mappedCarrier.left + mappedCarrier.width * oracle.drawCanvas.right / oracle.canvasWidth
        val bottom = mappedCarrier.top + mappedCarrier.height * oracle.drawCanvas.bottom / oracle.canvasHeight
        return ChromeVisualRegion(
            id = "oracle-draw-${request.sampleId}",
            left = floor(left).toInt().coerceAtLeast(mappedCarrier.left),
            top = floor(top).toInt().coerceAtLeast(mappedCarrier.top),
            right = ceil(right).toInt().coerceAtMost(mappedCarrier.right),
            bottom = ceil(bottom).toInt().coerceAtMost(mappedCarrier.bottom),
        ).takeIf { it.width > 0 && it.height > 0 }
    }
}

internal fun ChromeVisualShieldIdentity.renderIdentityToken(): String =
    listOf(
        protectionSessionId,
        windowId,
        viewportEpoch,
        viewport.left,
        viewport.top,
        viewport.right,
        viewport.bottom,
        regionId,
        region.left,
        region.top,
        region.right,
        region.bottom,
    ).joinToString(":")
