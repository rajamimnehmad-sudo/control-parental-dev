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
    fun `lab routes every bounded resolved IPv4 and IPv6 address exactly`() {
        assertEquals(
            listOf(
                ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32),
                ChromePhotosLabVpnRoute("203.0.113.8", 32),
                ChromePhotosLabVpnRoute("2001:db8:0:0:0:0:0:8", 128),
            ),
            ChromePhotosDataPlaneLabVpnPolicy.routes(
                active = true,
                resolvedAddresses = listOf("203.0.113.8", "2001:db8::8", "203.0.113.8"),
            ),
        )
    }

    @Test
    fun `UDP fixture route is off by default and exact when gate is enabled`() {
        assertEquals(
            listOf(ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32)),
            ChromePhotosDataPlaneLabVpnPolicy.routes(active = true),
        )
        assertEquals(
            listOf(
                ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32),
                ChromePhotosLabVpnRoute("192.168.0.20", 32),
            ),
            ChromePhotosDataPlaneLabVpnPolicy.routes(
                active = true,
                udpFixtureAddress = "192.168.0.20",
            ),
        )
        assertEquals(
            listOf(ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32)),
            ChromePhotosDataPlaneLabVpnPolicy.routes(
                active = true,
                udpFixtureAddress = "8.8.8.8",
            ),
        )
        assertEquals(
            listOf(ChromePhotosLabVpnRoute(ChromePhotosDataPlaneLabContract.FixtureIpv4, 32)),
            ChromePhotosDataPlaneLabVpnPolicy.routes(
                active = true,
                udpFixtureAddress = "localhost",
            ),
        )
    }

    @Test
    fun `UDP fixture VPN admission is opt in and bounded to the fixture package`() {
        assertEquals(emptySet(), ChromePhotosDataPlaneLabVpnPolicy.additionalAllowedPackages(null))
        assertEquals(
            setOf(ChromePhotosDataPlaneLabContract.UdpFixturePackage),
            ChromePhotosDataPlaneLabVpnPolicy.additionalAllowedPackages(
                ChromePhotosUdpFixtureGate(
                    address = "192.168.0.20",
                    port = 32_123,
                    malformedProbeEnabled = false,
                ),
            ),
        )
    }

    @Test
    fun `full tunnel DEV routes are off by default and dual stack when explicit transport gate is active`() {
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isFullTunnelDevGateEnabled(
                active = true,
                currentSessionId = "session-a",
                enabled = false,
                gateSessionId = "session-a",
            ),
        )
        assertEquals(emptyList(), ChromePhotosDataPlaneLabVpnPolicy.fullTunnelRoutes(enabled = false))
        assertTrue(
            ChromePhotosDataPlaneLabVpnPolicy.isFullTunnelDevGateEnabled(
                active = true,
                currentSessionId = "session-a",
                enabled = true,
                gateSessionId = "session-a",
            ),
        )
        assertEquals(
            listOf(
                ChromePhotosLabVpnRoute("0.0.0.0", 0),
                ChromePhotosLabVpnRoute("::", 0),
            ),
            ChromePhotosDataPlaneLabVpnPolicy.fullTunnelRoutes(enabled = true),
        )
        assertFalse(
            ChromePhotosDataPlaneLabVpnPolicy.isFullTunnelDevGateEnabled(
                active = true,
                currentSessionId = "session-b",
                enabled = true,
                gateSessionId = "session-a",
            ),
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
