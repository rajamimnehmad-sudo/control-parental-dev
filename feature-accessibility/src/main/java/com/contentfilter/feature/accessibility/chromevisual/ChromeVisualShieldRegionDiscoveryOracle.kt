package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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
            oracle.carrierCss.mapFromVisualViewport(identity.viewport, oracle.visualViewportCss)
                ?: return null
        if (!mappedCarrier.approximatelyMatches(identity.region, identity.viewport)) return null

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

    private fun ChromeVisualShieldLabRect.mapFromVisualViewport(
        target: ChromeVisualViewport,
        visualViewport: ChromeVisualShieldLabRect,
    ): ChromeVisualRegion? {
        val leftRatio = (left - visualViewport.left) / visualViewport.width
        val topRatio = (top - visualViewport.top) / visualViewport.height
        val rightRatio = (right - visualViewport.left) / visualViewport.width
        val bottomRatio = (bottom - visualViewport.top) / visualViewport.height
        if (listOf(leftRatio, topRatio, rightRatio, bottomRatio).any { !it.isFinite() || it !in 0.0..1.0 }) {
            return null
        }
        return ChromeVisualRegion(
            id = "oracle-carrier",
            left = floor(target.left + target.width * leftRatio).toInt(),
            top = floor(target.top + target.height * topRatio).toInt(),
            right = ceil(target.left + target.width * rightRatio).toInt(),
            bottom = ceil(target.top + target.height * bottomRatio).toInt(),
        ).takeIf { it.width > 0 && it.height > 0 }
    }

    private fun ChromeVisualRegion.approximatelyMatches(
        expected: ChromeVisualRegion,
        viewport: ChromeVisualViewport,
    ): Boolean {
        val tolerance = max(3, max(viewport.width, viewport.height) / 500)
        return abs(left - expected.left) <= tolerance &&
            abs(top - expected.top) <= tolerance &&
            abs(right - expected.right) <= tolerance &&
            abs(bottom - expected.bottom) <= tolerance
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
