package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChromeVisualShieldRegionDiscoveryHandshakeTest {
    @Test
    fun `one hundred equivalent geometry events begin one fixture render`() {
        val fixture = Fixture()

        repeat(100) { fixture.request(SessionA, KeyA) }

        assertEquals(1, fixture.beginCount)
        assertEquals(1, fixture.policy.metrics().beginFixtureRenderCount)
        assertEquals(99, fixture.policy.metrics().reused)
    }

    @Test
    fun `duplicate key while in flight does not begin more work`() {
        val fixture = Fixture()

        assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(
            fixture.request(SessionA, KeyA),
        )
        val duplicate =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(
                fixture.request(SessionA, KeyA),
            )

        assertEquals(ChromeVisualShieldRegionDiscoveryHandshakePhase.InFlight, duplicate.phase)
        assertEquals(1, fixture.beginCount)
    }

    @Test
    fun `opaque publication with the same key remains a no-op`() {
        val fixture = Fixture()
        fixture.request(SessionA, KeyA)

        val afterOpaquePublication = fixture.request(SessionA, KeyA)

        assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(afterOpaquePublication)
        assertEquals(1, fixture.beginCount)
    }

    @Test
    fun `completed key does not begin again after equivalent resize`() {
        val fixture = Fixture()
        val started =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(
                fixture.request(SessionA, KeyA),
            )
        val claim =
            assertNotNull(
                fixture.policy.claimAttestation(SessionA, started.renderIdentityToken, started.renderKeyDigest),
            )
        assertEquals(true, fixture.policy.executeAttestation(claim) { true })

        val afterResize =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(
                fixture.request(SessionA, KeyA),
            )

        assertEquals(ChromeVisualShieldRegionDiscoveryHandshakePhase.Attested, afterResize.phase)
        assertEquals(1, fixture.beginCount)
    }

    @Test
    fun `real geometry change from A to B begins exactly one new render`() {
        val fixture = Fixture()

        fixture.request(SessionA, KeyA)
        fixture.request(SessionA, KeyB)

        assertEquals(2, fixture.beginCount)
        assertEquals(2, fixture.policy.metrics().beginFixtureRenderCount)
    }

    @Test
    fun `duplicate events for B do not add renders`() {
        val fixture = Fixture()

        fixture.request(SessionA, KeyA)
        repeat(50) { fixture.request(SessionA, KeyB) }

        assertEquals(2, fixture.beginCount)
    }

    @Test
    fun `orientation and geometry change to C begins a third render`() {
        val fixture = Fixture()

        fixture.request(SessionA, KeyA)
        fixture.request(SessionA, KeyB)
        fixture.request(SessionA, KeyC)

        assertEquals(3, fixture.beginCount)
    }

    @Test
    fun `attestation rejection never auto retries the same key`() {
        val fixture = Fixture()
        val started =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(
                fixture.request(SessionA, KeyA),
            )
        val claim =
            assertNotNull(
                fixture.policy.claimAttestation(SessionA, started.renderIdentityToken, started.renderKeyDigest),
            )
        assertEquals(false, fixture.policy.executeAttestation(claim) { false })

        val repeated =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse>(
                fixture.request(SessionA, KeyA),
            )

        assertEquals(ChromeVisualShieldRegionDiscoveryHandshakePhase.Rejected, repeated.phase)
        assertEquals(1, fixture.beginCount)
        assertEquals(1, fixture.policy.metrics().attestationRejected)
    }

    @Test
    fun `new native session permits the same geometry once`() {
        val fixture = Fixture()

        fixture.request(SessionA, KeyA)
        fixture.request(SessionB, KeyA)
        fixture.request(SessionB, KeyA)

        assertEquals(2, fixture.beginCount)
    }

    @Test
    fun `stale key cannot complete the newer identity`() {
        val fixture = Fixture()
        val first =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(
                fixture.request(SessionA, KeyA),
            )
        val staleClaim =
            assertNotNull(
                fixture.policy.claimAttestation(SessionA, first.renderIdentityToken, first.renderKeyDigest),
            )
        fixture.request(SessionA, KeyB)
        var staleActionCalls = 0

        val staleResult =
            fixture.policy.executeAttestation(staleClaim) {
                staleActionCalls += 1
                true
            }

        assertNull(staleResult)
        assertEquals(0, staleActionCalls)
        assertEquals(1, fixture.policy.metrics().staleAttestationDropped)
        assertEquals(2, fixture.beginCount)
    }

    @Test
    fun `duplicate attestation cannot invoke native capture twice`() {
        val fixture = Fixture()
        val started =
            assertIs<ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired>(
                fixture.request(SessionA, KeyA),
            )
        val firstClaim =
            assertNotNull(
                fixture.policy.claimAttestation(SessionA, started.renderIdentityToken, started.renderKeyDigest),
            )
        var nativeCalls = 0
        assertEquals(
            true,
            fixture.policy.executeAttestation(firstClaim) {
                nativeCalls += 1
                true
            },
        )

        val duplicateClaim =
            fixture.policy.claimAttestation(SessionA, started.renderIdentityToken, started.renderKeyDigest)

        assertNull(duplicateClaim)
        assertEquals(1, nativeCalls)
        assertEquals(1, fixture.policy.metrics().attestationClaims)
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

        fun request(
            session: ChromeVisualShieldRegionDiscoveryNativeSession,
            key: ChromeVisualShieldRegionDiscoveryRenderGeometryKey,
        ): ChromeVisualShieldRegionDiscoveryHandshakeRequestResult =
            policy.request(session, key) {
                beginCount += 1
                token(session)
            }
    }

    private companion object {
        val SessionA = ChromeVisualShieldRegionDiscoveryNativeSession(7, 11)
        val SessionB = ChromeVisualShieldRegionDiscoveryNativeSession(8, 11)
        val KeyA = key(width = 412.0, orientation = "portrait-primary")
        val KeyB = key(width = 411.5, orientation = "portrait-primary")
        val KeyC = key(width = 772.0, orientation = "landscape-primary")

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

        fun token(session: ChromeVisualShieldRegionDiscoveryNativeSession): String =
            "${session.protectionSessionId}:${session.windowId}:13:0:0:1080:2408:fixture:162:602:918:1324"
    }
}
