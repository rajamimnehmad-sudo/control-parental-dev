package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeVisualShieldExactDrawOracleTest {
    @Test
    fun `portrait oracle maps only the contained draw inside the signed carrier`() {
        val identity = identity(ChromeVisualViewport(0, 0, 1080, 2408))
        val request = request(identity, sourceWidth = 1064, sourceHeight = 1600)

        val region = assertNotNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, request))

        assertTrue(region.left > identity.region.left)
        assertEquals(identity.region.top, region.top)
        assertTrue(region.right < identity.region.right)
        assertEquals(identity.region.bottom, region.bottom)
        assertEquals("oracle-draw-block", region.id)
    }

    @Test
    fun `landscape oracle removes neutral side background without stretching the source`() {
        val identity = identity(ChromeVisualViewport(66, 0, 2408, 1080))
        val request = request(identity, sourceWidth = 1064, sourceHeight = 1600)

        val region = assertNotNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, request))

        assertTrue(region.width * 5 < identity.region.width)
        assertEquals(identity.region.top, region.top)
        assertEquals(identity.region.bottom, region.bottom)
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
    fun `carrier geometry outside the signed current region is rejected`() {
        val identity = identity(ChromeVisualViewport(0, 0, 1080, 2408))
        val request = request(identity, 1064, 1600)
        val shifted =
            request.copy(
                exactDrawOracle =
                    request.exactDrawOracle?.copy(
                        carrierCss = request.exactDrawOracle.carrierCss.copy(left = 300.0),
                    ),
            )

        assertNull(ChromeVisualShieldExactDrawOracleMapper.resolve(identity, shifted))
    }

    private fun request(
        identity: ChromeVisualShieldIdentity,
        sourceWidth: Int,
        sourceHeight: Int,
    ): ChromeVisualShieldRenderProbeRequest {
        val carrier = identity.region
        val scale = minOf(carrier.width.toDouble() / sourceWidth, carrier.height.toDouble() / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val oracle =
            ChromeVisualShieldExactDrawOracle(
                renderIdentityToken = identity.renderIdentityToken(),
                sourceSha256 = SourceSha,
                renderContract = RenderContract,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                canvasWidth = carrier.width,
                canvasHeight = carrier.height,
                carrierCss =
                    ChromeVisualShieldLabRect(
                        carrier.left - identity.viewport.left.toDouble(),
                        carrier.top - identity.viewport.top.toDouble(),
                        carrier.width.toDouble(),
                        carrier.height.toDouble(),
                    ),
                visualViewportCss =
                    ChromeVisualShieldLabRect(
                        0.0,
                        0.0,
                        identity.viewport.width.toDouble(),
                        identity.viewport.height.toDouble(),
                    ),
                visualViewportScale = 1.0,
                devicePixelRatio = 1.0,
                drawCanvas =
                    ChromeVisualShieldLabRect(
                        (carrier.width - drawWidth) / 2.0,
                        (carrier.height - drawHeight) / 2.0,
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
