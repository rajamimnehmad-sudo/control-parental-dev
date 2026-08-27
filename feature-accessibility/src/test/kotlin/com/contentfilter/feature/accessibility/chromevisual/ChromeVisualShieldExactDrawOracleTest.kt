package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeVisualShieldExactDrawOracleTest {
    @Test
    fun `portrait oracle maps browser draw inside taller signed native carrier`() {
        val identity = identity(ChromeVisualViewport(0, 0, 1080, 2408))
        val request = request(identity, sourceWidth = 1064, sourceHeight = 1600, canvasHeight = 703)

        val region = assertNotNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, request))

        assertTrue(region.left > identity.region.left)
        assertTrue(region.right < identity.region.right)
        assertTrue(region.top > identity.region.top)
        assertTrue(region.bottom > identity.region.bottom)
        assertEquals(1064.0 / 1600.0, region.width.toDouble() / region.height, 0.01)
        assertEquals("oracle-draw-block", region.id)
    }

    @Test
    fun `landscape oracle removes neutral side background without stretching the source`() {
        val identity = identity(ChromeVisualViewport(66, 0, 2408, 1080))
        val request = request(identity, sourceWidth = 1064, sourceHeight = 1600, canvasHeight = 304)

        val region = assertNotNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, request))

        assertTrue(region.width * 5 < identity.region.width)
        assertTrue(region.top > identity.region.top)
        assertTrue(region.bottom > identity.region.bottom)
        assertEquals(1064.0 / 1600.0, region.width.toDouble() / region.height, 0.01)
    }

    @Test
    fun `oracle from stale render identity is rejected`() {
        val identity = identity(ChromeVisualViewport(0, 0, 1080, 2408))
        val request = request(identity, 1064, 1600)
        val stale = request.copy(exactDrawOracle = request.exactDrawOracle?.copy(renderIdentityToken = "stale"))

        assertNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, stale))
    }

    @Test
    fun `canvas backing inconsistent with attested browser carrier is rejected`() {
        val identity = identity(ChromeVisualViewport(0, 0, 1080, 2408))
        val request = request(identity, 1064, 1600)
        val inconsistent =
            request.copy(
                exactDrawOracle =
                    request.exactDrawOracle?.copy(
                        canvasWidth = request.exactDrawOracle.canvasWidth + 20,
                    ),
            )

        assertNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, inconsistent))
    }

    private fun request(
        identity: ChromeVisualShieldIdentity,
        sourceWidth: Int,
        sourceHeight: Int,
        canvasHeight: Int = identity.region.height,
    ): ChromeVisualShieldRenderProbeRequest {
        val carrier = identity.region
        val canvasWidth = carrier.width
        val scale = minOf(canvasWidth.toDouble() / sourceWidth, canvasHeight.toDouble() / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val visualViewportHeight = canvasHeight / 0.3
        val visualViewportWidth = canvasWidth / 0.7
        val oracle =
            ChromeVisualShieldExactDrawOracle(
                renderIdentityToken = identity.renderIdentityToken(),
                sourceSha256 = SourceSha,
                renderContract = RenderContract,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                carrierCss =
                    ChromeVisualShieldLabRect(
                        visualViewportWidth * 0.15,
                        visualViewportHeight * 0.25,
                        canvasWidth.toDouble(),
                        canvasHeight.toDouble(),
                    ),
                visualViewportCss =
                    ChromeVisualShieldLabRect(
                        0.0,
                        0.0,
                        visualViewportWidth,
                        visualViewportHeight,
                    ),
                visualViewportScale = 1.0,
                devicePixelRatio = 1.0,
                drawCanvas =
                    ChromeVisualShieldLabRect(
                        (canvasWidth - drawWidth) / 2.0,
                        (canvasHeight - drawHeight) / 2.0,
                        drawWidth,
                        drawHeight,
                    ),
            )
        return ChromeVisualShieldRenderProbeRequest(
            sampleId = "block",
            sourceSha256 = SourceSha,
            renderContract = RenderContract,
            exactDrawOracleRequired = true,
            exactDrawOracle = oracle,
        )
    }

    private fun identity(viewport: ChromeVisualViewport): ChromeVisualShieldIdentity {
        val region =
            ChromeVisualShieldRegionContract("fixture", 1500, 2500, 8500, 5500, "signed")
                .resolve(viewport)!!
        return ChromeVisualShieldIdentity(
            protectionSessionId = 7,
            windowId = 13,
            contentEpoch = 17,
            viewport = viewport,
            viewportEpoch = 19,
            captureSequence = 23,
            regionId = region.id,
            regionSequence = 29,
            region = region,
        )
    }

    private companion object {
        const val SourceSha = "9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94"
        const val RenderContract = "canvas-contain-neutral-v1"
    }
}
