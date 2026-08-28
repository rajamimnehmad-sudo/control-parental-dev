package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryPresentationBarrierTest {
    @Test
    fun `current binding and exact raster marker prove presentation outside discovery crop`() {
        val fixture = Fixture()
        fixture.draw(fixture.proof)

        val result = assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Proven>(fixture.verify())

        assertEquals(fixture.proof.pattern.length, result.matchedSamples)
        assertTrue(result.markerBounds.bottom <= fixture.identity.region.top)
        assertEquals(1, fixture.barrier.snapshot().observed)
    }

    @Test
    fun `missing partial and stale markers never prove presentation`() {
        val absent = Fixture()
        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerAbsent,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(absent.verify()).reason,
        )

        val partial = Fixture()
        partial.draw(partial.proof, cells = partial.proof.pattern.length / 2)
        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerStaleOrCorrupt,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(partial.verify()).reason,
        )

        val stale = Fixture()
        val staleProof =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                    stale.binding.copy(contentEpoch = stale.binding.contentEpoch + 1),
                    CanvasWidth,
                    CanvasHeight,
                ),
            )
        stale.draw(staleProof)
        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerStaleOrCorrupt,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(stale.verify()).reason,
        )
    }

    @Test
    fun `foreign generation rotation and declared geometry mismatch fail closed`() {
        val fixture = Fixture()
        fixture.draw(fixture.proof)

        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.BindingMismatch,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(
                fixture.verify(binding = fixture.binding.copy(regionSequence = fixture.binding.regionSequence + 1)),
            ).reason,
        )
        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.BindingMismatch,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(
                fixture.verify(
                    identity =
                        fixture.identity.copy(
                            viewportEpoch = fixture.identity.viewportEpoch + 1,
                            viewport = ChromeVisualViewport(0, 0, FrameHeight, FrameWidth),
                        ),
                ),
            ).reason,
        )
        assertEquals(
            ChromeVisualShieldRegionDiscoveryPresentationRejectReason.ProofContractMismatch,
            assertIs<ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected>(
                fixture.verify(
                    oracle =
                        fixture.oracle.copy(
                            presentationProof =
                                fixture.proof.copy(
                                    markerCanvas = fixture.proof.markerCanvas.copy(top = 1.0),
                                ),
                        ),
                ),
            ).reason,
        )
    }

    @Test
    fun `marker signature binds every generation field and verifier is read only`() {
        val fixture = Fixture()
        val original = fixture.pixels.copyOf()
        val variants =
            listOf(
                fixture.binding.copy(protectionSessionId = 8),
                fixture.binding.copy(windowId = 12),
                fixture.binding.copy(contentEpoch = 22),
                fixture.binding.copy(viewportEpoch = 14),
                fixture.binding.copy(regionSequence = 22),
                fixture.binding.copy(renderIdentityToken = "other"),
                fixture.binding.copy(renderGeometryKeyDigest = "b".repeat(64)),
            )

        variants.forEach { variant ->
            val proof =
                assertNotNull(
                    ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                        variant,
                        CanvasWidth,
                        CanvasHeight,
                    ),
                )
            assertNotEquals(fixture.proof.pattern, proof.pattern)
        }
        fixture.verify()
        assertContentEquals(original, fixture.pixels)
    }

    private class Fixture {
        val barrier = ChromeVisualShieldRegionDiscoveryPresentationBarrier()
        val pixels = IntArray(FrameWidth * FrameHeight) { 0xff202428.toInt() }
        val identity =
            ChromeVisualShieldIdentity(
                protectionSessionId = 7,
                windowId = 11,
                contentEpoch = 21,
                viewport = ChromeVisualViewport(0, 0, FrameWidth, FrameHeight),
                viewportEpoch = 13,
                captureSequence = 1,
                regionId = "fixture",
                regionSequence = 21,
                region = ChromeVisualRegion("fixture", 154, 254, 846, 546),
            )
        val binding =
            ChromeVisualShieldRegionDiscoveryRenderBinding(
                protectionSessionId = identity.protectionSessionId,
                windowId = identity.windowId,
                contentEpoch = identity.contentEpoch,
                viewportEpoch = identity.viewportEpoch,
                regionSequence = identity.regionSequence,
                renderIdentityToken = identity.renderIdentityToken(),
                renderGeometryKeyDigest = "a".repeat(64),
            )
        val proof =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                    binding,
                    CanvasWidth,
                    CanvasHeight,
                ),
            )
        val oracle =
            ChromeVisualShieldRegionDiscoveryOracle(
                renderIdentityToken = binding.renderIdentityToken,
                scenarioId = "centered-safe",
                renderContract = "canvas-content-islands-v3",
                canvasWidth = CanvasWidth,
                canvasHeight = CanvasHeight,
                carrierCss = ChromeVisualShieldLabRect(150.0, 250.0, 700.0, 300.0),
                visualViewportCss = ChromeVisualShieldLabRect(0.0, 0.0, 1000.0, 1000.0),
                visualViewportScale = 1.0,
                devicePixelRatio = 1.0,
                presentationProof = proof,
                expectComplete = true,
                regions =
                    listOf(
                        ChromeVisualShieldRegionDiscoveryOracleRegion(
                            "oracle-safe",
                            "1".repeat(64),
                            100,
                            100,
                            ChromeVisualShieldLabRect(200.0, 50.0, 300.0, 200.0),
                        ),
                    ),
            )

        fun verify(
            identity: ChromeVisualShieldIdentity = this.identity,
            binding: ChromeVisualShieldRegionDiscoveryRenderBinding = this.binding,
            oracle: ChromeVisualShieldRegionDiscoveryOracle = this.oracle,
        ) = barrier.verify(FrameWidth, FrameHeight, { x, y -> pixels[y * FrameWidth + x] }, identity, binding, oracle)

        fun draw(
            value: ChromeVisualShieldRegionDiscoveryPresentationProof,
            cells: Int = value.pattern.length,
        ) {
            value.pattern.take(cells).forEachIndexed { index, bit ->
                val color =
                    if (bit == '0') {
                        ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.ZeroRgb
                    } else {
                        ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.OneRgb
                    }
                val left = 150 + value.markerCanvas.left.toInt() + index * value.cellWidth
                val top = 250 + value.markerCanvas.top.toInt()
                repeat(value.markerCanvas.height.toInt()) { y ->
                    repeat(value.cellWidth) { x ->
                        pixels[(top + y) * FrameWidth + left + x] = 0xff000000.toInt() or color
                    }
                }
            }
        }
    }

    private companion object {
        const val FrameWidth = 1000
        const val FrameHeight = 1000
        const val CanvasWidth = 700
        const val CanvasHeight = 300
    }
}
