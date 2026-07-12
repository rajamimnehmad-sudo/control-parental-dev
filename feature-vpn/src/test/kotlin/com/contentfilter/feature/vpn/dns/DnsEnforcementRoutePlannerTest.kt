package com.contentfilter.feature.vpn.dns

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsEnforcementRoutePlannerTest {
    @Test
    fun `open DNS mode captures IPv4 and IPv6 upstream resolvers`() {
        val routes =
            DnsEnforcementRoutePlanner.hostRoutes(
                upstreamDnsServers =
                    listOf(
                        InetAddress.getByName("192.0.2.53"),
                        InetAddress.getByName("2001:db8::53"),
                    ),
                includeEncryptedDnsResolvers = false,
            )

        assertEquals(setOf(32, 128), routes.map { it.prefixLength }.toSet())
        assertTrue(routes.hasAddress("192.0.2.53"))
        assertTrue(routes.hasAddress("2001:db8::53"))
        assertFalse(routes.hasAddress("8.8.8.8"))
    }

    @Test
    fun `SafeSearch routes known DoH DoT and QUIC resolver addresses`() {
        val routes =
            DnsEnforcementRoutePlanner.hostRoutes(
                upstreamDnsServers = emptyList(),
                includeEncryptedDnsResolvers = true,
            )

        assertTrue(routes.hasAddress("8.8.8.8"))
        assertTrue(routes.hasAddress("1.1.1.1"))
        assertTrue(routes.hasAddress("9.9.9.9"))
        assertTrue(routes.hasAddress("2001:4860:4860::8888"))
        assertTrue(routes.hasAddress("2606:4700:4700::1111"))
    }

    @Test
    fun `five enforcement cycles keep routes deterministic and deduplicated`() {
        val upstream = InetAddress.getByName("8.8.8.8")

        repeat(5) {
            val routes =
                DnsEnforcementRoutePlanner.hostRoutes(
                    upstreamDnsServers = listOf(upstream, upstream),
                    includeEncryptedDnsResolvers = true,
                )

            assertEquals(routes.map { it.address.address.toList() }.distinct().size, routes.size)
            assertEquals(1, routes.count { it.address == upstream })
        }
    }

    private fun List<DnsEnforcementRoute>.hasAddress(address: String): Boolean {
        val expected = InetAddress.getByName(address)
        return any { it.address == expected }
    }
}
