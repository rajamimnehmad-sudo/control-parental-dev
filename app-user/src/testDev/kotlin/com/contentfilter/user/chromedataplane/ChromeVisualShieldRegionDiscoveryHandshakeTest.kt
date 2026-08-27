package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryNativeGeneration
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryRenderBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChromeVisualShieldRegionDiscoveryHandshakeTest {
    @Test
    fun `one hundred equivalent geometry events begin one fixture render`() {
        val fixture = Fixture()

        repeat(100) { fixture.request(KeyA) }

        assertEquals(1, fixture.beginCount)
        assertEquals(1, fixture.policy.metrics().beginFixtureRenderCount)
        assertEquals(99, fixture.policy.metrics().reused)
    }

    @Test
    fun `same generation duplicates remain reuse after attestation`() {
        val fixture = Fixture()
        val started = fixture.newRender(KeyA)
        val claim = assertNotNull(fixture.policy.claimAttestation(fixture.current, started.binding))

        assertEquals(true, fixture.policy.executeAttestation(claim, fixture.current) { true })
        val repeated = assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(fixture.request(KeyA))

        assertEquals(ChromeVisualShieldRegionDiscoveryHandshakePhase.Attested, repeated.phase)
        assertEquals(1, fixture.beginCount)
    }

    @Test
    fun `same geometry in a new native generation requires a new render`() {
        val fixture = Fixture()
        fixture.request(KeyA)
        fixture.invalidateGeneration()

        val second = fixture.newRender(KeyA)

        assertEquals(fixture.current, second.binding.generation())
        assertEquals(2, fixture.beginCount)
        assertEquals(1, fixture.policy.metrics().generationReplacements)
    }

    @Test
    fun `real geometry and orientation changes each begin one generation`() {
        val fixture = Fixture()

        fixture.request(KeyA)
        fixture.request(KeyB)
        fixture.request(KeyC)
        repeat(50) { fixture.request(KeyC) }

        assertEquals(3, fixture.beginCount)
    }

    @Test
    fun `attestation rejection never auto retries same generation and key`() {
        val fixture = Fixture()
        val started = fixture.newRender(KeyA)
        val claim = assertNotNull(fixture.policy.claimAttestation(fixture.current, started.binding))
        assertEquals(false, fixture.policy.executeAttestation(claim, fixture.current) { false })

        val repeated = assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(fixture.request(KeyA))

        assertEquals(ChromeVisualShieldRegionDiscoveryHandshakePhase.Rejected, repeated.phase)
        assertEquals(1, fixture.beginCount)
    }

    @Test
    fun `stale generation cannot execute attestation callback`() {
        val fixture = Fixture()
        val first = fixture.newRender(KeyA)
        val staleClaim = assertNotNull(fixture.policy.claimAttestation(fixture.current, first.binding))
        fixture.invalidateGeneration()
        var actionCalls = 0

        val result =
            fixture.policy.executeAttestation(staleClaim, fixture.current) {
                actionCalls += 1
                true
            }

        assertNull(result)
        assertEquals(0, actionCalls)
        assertEquals(1, fixture.policy.metrics().staleAttestationDropped)
    }

    @Test
    fun `stale binding cannot claim attestation for current generation`() {
        val fixture = Fixture()
        val first = fixture.newRender(KeyA)
        fixture.invalidateGeneration()

        assertNull(fixture.policy.claimAttestation(fixture.current, first.binding))
        assertEquals(1, fixture.policy.metrics().staleAttestationDropped)
    }

    @Test
    fun `duplicate attestation never invokes native callback twice`() {
        val fixture = Fixture()
        val started = fixture.newRender(KeyA)
        val firstClaim = assertNotNull(fixture.policy.claimAttestation(fixture.current, started.binding))
        var nativeCalls = 0
        assertEquals(
            true,
            fixture.policy.executeAttestation(firstClaim, fixture.current) {
                nativeCalls += 1
                true
            },
        )

        assertNull(fixture.policy.claimAttestation(fixture.current, started.binding))
        assertEquals(1, nativeCalls)
    }

    @Test
    fun `new session and window each require a fresh render`() {
        val fixture = Fixture()
        fixture.request(KeyA)

        fixture.current = fixture.current.copy(protectionSessionId = 8, contentEpoch = 1, regionSequence = 1)
        fixture.request(KeyA)
        fixture.current = fixture.current.copy(windowId = 12, contentEpoch = 2, regionSequence = 2)
        fixture.request(KeyA)

        assertEquals(3, fixture.beginCount)
    }

    @Test
    fun `render key parser pins every required geometry field`() {
        val encoded =
            listOf(
                ChromeVisualShieldRegionDiscoveryScenario.CenteredSafe.wireName,
                ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
                ChromeVisualShieldFixtureSample.Safe.expectedSha256,
                "portrait-primary",
                "0",
                "24.5",
                "412",
                "772.25",
                "1",
                "2.625",
                "61.8",
                "96",
                "288.4",
                "240",
                "757",
                "630",
            ).joinToString("|")

        val parsed =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryRenderGeometryKey.parse(
                    ChromeVisualShieldRegionDiscoveryScenario.CenteredSafe,
                    encoded,
                ),
            )

        assertEquals("portrait-primary", parsed.orientation)
        assertEquals(24.5, parsed.visualViewportOffsetTop)
        assertEquals(772.25, parsed.visualViewportHeight)
        assertEquals(2.625, parsed.devicePixelRatio)
        assertEquals(757, parsed.canvasBackingWidth)
        assertEquals(64, parsed.digest.length)
        assertNull(
            ChromeVisualShieldRegionDiscoveryRenderGeometryKey.parse(
                ChromeVisualShieldRegionDiscoveryScenario.CenteredBlock,
                encoded,
            ),
        )
    }

    private class Fixture {
        val policy = ChromeVisualShieldRegionDiscoveryHandshakePolicy()
        var beginCount = 0
        var current = GenerationA

        fun request(key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey) =
            policy.request(current, key) {
                beginCount += 1
                current = current.nextGeneration()
                binding(current, key)
            }

        fun newRender(key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey) =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(request(key))

        fun invalidateGeneration() {
            current = current.nextGeneration()
        }
    }

    private companion object {
        val GenerationA = generation(session = 7, window = 11, content = 20, viewport = 13, region = 20)
        val KeyA = key(width = 412.0, orientation = "portrait-primary")
        val KeyB = key(width = 411.5, orientation = "portrait-primary")
        val KeyC = key(width = 772.0, orientation = "landscape-primary")

        fun generation(
            session: Long,
            window: Int,
            content: Long,
            viewport: Long,
            region: Long,
        ) = ChromeVisualShieldRegionDiscoveryNativeGeneration(
            protectionSessionId = session,
            windowId = window,
            contentEpoch = content,
            viewportEpoch = viewport,
            regionSequence = region,
            renderIdentityToken = "$session:$window:$viewport:0:0:1080:2408:fixture:162:602:918:1324",
        )

        fun ChromeVisualShieldRegionDiscoveryNativeGeneration.nextGeneration() =
            copy(contentEpoch = contentEpoch + 1, regionSequence = regionSequence + 1)

        fun binding(
            generation: ChromeVisualShieldRegionDiscoveryNativeGeneration,
            key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        ) = ChromeVisualShieldRegionDiscoveryRenderBinding(
            protectionSessionId = generation.protectionSessionId,
            windowId = generation.windowId,
            contentEpoch = generation.contentEpoch,
            viewportEpoch = generation.viewportEpoch,
            regionSequence = generation.regionSequence,
            renderIdentityToken = generation.renderIdentityToken,
            renderGeometryKeyDigest = key.digest,
        )

        fun key(
            width: Double,
            orientation: String,
        ) = ChromeVisualShieldRegionDiscoveryRenderGeometryKey(
            scenarioId = ChromeVisualShieldRegionDiscoveryScenario.CenteredSafe.wireName,
            layoutContract = ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
            sourceSha256s = listOf(ChromeVisualShieldFixtureSample.Safe.expectedSha256),
            orientation = orientation,
            visualViewportOffsetLeft = 0.0,
            visualViewportOffsetTop = 0.0,
            visualViewportWidth = width,
            visualViewportHeight = 772.0,
            visualViewportScale = 1.0,
            devicePixelRatio = 2.625,
            canvasCssLeft = width * 0.15,
            canvasCssTop = 96.0,
            canvasCssWidth = width * 0.70,
            canvasCssHeight = 240.0,
            canvasBackingWidth = (width * 0.70 * 2.625).toInt(),
            canvasBackingHeight = 630,
        )
    }
}
