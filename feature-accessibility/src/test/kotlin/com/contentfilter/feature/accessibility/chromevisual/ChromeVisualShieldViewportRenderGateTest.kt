package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChromeVisualShieldViewportRenderGateTest {
    @Test
    fun `viewport boundary remains protected until current redraw attestation and opaque commit`() {
        val gate = ChromeVisualShieldViewportRenderGate()
        val landscape = context(viewportEpoch = 2, viewport = ChromeVisualViewport(0, 0, 2200, 1080))
        val token = landscape.renderIdentityToken()

        gate.requireCurrentRender(landscape)

        assertTrue(gate.isWaiting(landscape))
        assertFalse(gate.consumeCapturePermission(landscape))
        assertTrue(gate.recordAttestation(token, landscape))
        assertFalse(gate.consumeCapturePermission(landscape))
        gate.recordOpaqueCommit(landscape)
        assertTrue(gate.consumeCapturePermission(landscape))
        assertTrue(gate.consumeCapturePermission(landscape))
    }

    @Test
    fun `pre rotation attestation cannot satisfy post rotation viewport epoch`() {
        val gate = ChromeVisualShieldViewportRenderGate()
        val portrait = context(viewportEpoch = 1, viewport = ChromeVisualViewport(0, 0, 1080, 2200))
        val landscape = context(viewportEpoch = 2, viewport = ChromeVisualViewport(0, 0, 2200, 1080))

        gate.requireCurrentRender(landscape)
        gate.recordOpaqueCommit(landscape)

        assertNotEquals(portrait.renderIdentityToken(), landscape.renderIdentityToken())
        assertFalse(gate.recordAttestation(portrait.renderIdentityToken(), landscape))
        assertFalse(gate.consumeCapturePermission(landscape))
    }

    @Test
    fun `portrait landscape portrait cycles require each new viewport epoch independently`() {
        val gate = ChromeVisualShieldViewportRenderGate()
        val portrait1 = context(viewportEpoch = 1, viewport = ChromeVisualViewport(0, 0, 1080, 2200))
        val landscape = context(viewportEpoch = 2, viewport = ChromeVisualViewport(0, 0, 2200, 1080))
        val portrait2 = context(viewportEpoch = 3, viewport = ChromeVisualViewport(0, 0, 1080, 2200))

        listOf(landscape, portrait2).forEach { current ->
            gate.requireCurrentRender(current)
            gate.recordOpaqueCommit(current)
            assertFalse(gate.recordAttestation(portrait1.renderIdentityToken(), current))
            assertFalse(gate.consumeCapturePermission(current))
            assertTrue(gate.recordAttestation(current.renderIdentityToken(), current))
            assertTrue(gate.consumeCapturePermission(current))
        }
    }

    @Test
    fun `current attestation outside a viewport boundary is accepted without creating authority`() {
        val gate = ChromeVisualShieldViewportRenderGate()
        val current = context(viewportEpoch = 4, viewport = ChromeVisualViewport(0, 0, 1080, 2200))

        assertTrue(gate.recordAttestation(current.renderIdentityToken(), current))
        assertTrue(gate.consumeCapturePermission(current))
    }

    private fun context(
        viewportEpoch: Long,
        viewport: ChromeVisualViewport,
    ): ChromeVisualShieldContext {
        val region =
            ChromeVisualShieldRegionContract("fixture", 1500, 2500, 8500, 5500, "signed")
                .resolve(viewport)!!
        return ChromeVisualShieldContext(
            protectionSessionId = 1,
            windowId = 7,
            contentEpoch = viewportEpoch + 10,
            viewport = viewport,
            viewportEpoch = viewportEpoch,
            regionId = region.id,
            regionSequence = viewportEpoch + 20,
            region = region,
        )
    }
}
