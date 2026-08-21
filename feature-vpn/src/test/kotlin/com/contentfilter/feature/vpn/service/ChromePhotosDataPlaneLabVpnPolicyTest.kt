package com.contentfilter.feature.vpn.service

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosDataPlaneLabVpnPolicyTest {
    @Test
    fun `lab adds only controlled fixture host route`() {
        assertEquals(emptyList(), ChromePhotosDataPlaneLabVpnPolicy.routes(active = false))
        assertEquals(
            listOf(ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32)),
            ChromePhotosDataPlaneLabVpnPolicy.routes(active = true),
        )
    }

    @Test
    fun `fixture DNS match is exact and gated`() {
        assertTrue(
            ChromePhotosDataPlaneLabVpnPolicy.isFixtureDomain(
                active = true,
                normalizedDomain = ChromePhotosDataPlaneLabContract.FixtureHost,
            ),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isFixtureDomain(
                active = false,
                normalizedDomain = ChromePhotosDataPlaneLabContract.FixtureHost,
            ),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isFixtureDomain(
                active = true,
                normalizedDomain = "other.example",
            ),
        )
    }

    @Test
    fun `fixture exposes IPv4 only so QUIC bypass cannot escape over IPv6`() {
        assertContentEquals(
            byteArrayOf(198.toByte(), 18, 0, 1),
            ChromePhotosDataPlaneLabVpnPolicy.fixtureAddresses(queryType = 1).single(),
        )
        assertEquals(emptyList(), ChromePhotosDataPlaneLabVpnPolicy.fixtureAddresses(queryType = 28))
    }

    @Test
    fun `VPN attestation requires active lab current session and established tunnel`() {
        assertTrue(
            ChromePhotosDataPlaneLabVpnPolicy.isTunnelConfirmed(
                active = true,
                sessionId = "session-a",
                established = true,
            ),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isTunnelConfirmed(
                active = false,
                sessionId = "session-a",
                established = true,
            ),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isTunnelConfirmed(
                active = true,
                sessionId = "",
                established = true,
            ),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isTunnelConfirmed(
                active = true,
                sessionId = "session-a",
                established = false,
            ),
        )
    }
}
