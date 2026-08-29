package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import com.contentfilter.feature.vpn.service.VpnTransportProtocol
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnTransportPolicyTest {
    private val controlled =
        VpnDestinationAuthority(
            controlledAddresses = setOf("203.0.113.8"),
            controlledPorts = setOf(80, 443),
            scope = VpnTransportScope.Controlled,
        )
    private val policy = VpnTransportPolicy("com.android.chrome", controlled)

    @Test
    fun `Chrome direct TCP and UDP 443 fail closed`() {
        val chrome = VpnConnectionOwnerResult.Resolved(10_222, listOf("com.android.chrome"))

        assertEquals(VpnTransportAction.DropChromeDirectHttps, policy.decide(flow(VpnTransportProtocol.Tcp), chrome))
        assertEquals(VpnTransportAction.DropChromeDirectHttps, policy.decide(flow(VpnTransportProtocol.Udp), chrome))
    }

    @Test
    fun `non Chrome controlled destination forwards and unknown drops`() {
        val other = VpnConnectionOwnerResult.Resolved(10_262, listOf("com.sec.android.app.sbrowser"))

        assertEquals(VpnTransportAction.ForwardToHev, policy.decide(flow(VpnTransportProtocol.Tcp), other))
        assertEquals(
            VpnTransportAction.DropUnknownOwner,
            policy.decide(
                flow(VpnTransportProtocol.Tcp),
                VpnConnectionOwnerResult.Unknown,
            ),
        )
    }

    @Test
    fun `resolved UID without package identity fails closed`() {
        assertEquals(
            VpnTransportAction.DropUnknownOwner,
            policy.decide(
                flow(VpnTransportProtocol.Tcp),
                VpnConnectionOwnerResult.Resolved(uid = 10_263, packages = emptyList()),
            ),
        )
    }

    @Test
    fun `DNS remains on existing pipeline and unapproved target never forwards`() {
        val other = VpnConnectionOwnerResult.Resolved(10_262, listOf("com.sec.android.app.sbrowser"))

        assertEquals(VpnTransportAction.ExistingDnsPath, policy.decide(flow(VpnTransportProtocol.Udp, 53), other))
        assertEquals(VpnTransportAction.ExistingDnsPath, policy.decide(flow(VpnTransportProtocol.Tcp, 53), other))
        assertEquals(
            VpnTransportAction.DropUnapprovedDestination,
            policy.decide(flow(VpnTransportProtocol.Tcp, address = "198.51.100.4"), other),
        )
    }

    @Test
    fun `full tunnel admits public IPv4 and IPv6 but rejects internal and DNS destinations`() {
        val authority =
            VpnDestinationAuthority(
                controlledAddresses = setOf("192.168.0.20"),
                controlledPorts = setOf(32_123),
                scope = VpnTransportScope.FullTunnelDev,
            )

        assertTrue(authority.isAllowed(address("8.8.8.8", 443)))
        assertTrue(authority.isAllowed(address("2606:4700:4700::1111", 443)))
        assertTrue(authority.isAllowed(address("192.168.0.20", 32_123)))
        assertFalse(authority.isAllowed(address("192.168.0.21", 443)))
        assertFalse(authority.isAllowed(address("127.0.0.1", 443)))
        assertFalse(authority.isAllowed(address("8.8.8.8", 53)))
        assertFalse(authority.isAllowed(address("8.8.8.8", 853)))
    }

    @Test
    fun `Chrome only authorizes exact local proxy and all external transport drops`() {
        val chrome = VpnConnectionOwnerResult.Resolved(10_222, listOf("com.android.chrome"))

        assertEquals(
            VpnTransportAction.AuthorizedChromeProxyPath,
            policy.decide(flow(VpnTransportProtocol.Tcp, 8877, "127.0.0.1"), chrome),
        )
        assertEquals(
            VpnTransportAction.DropChromeDirectHttps,
            policy.decide(flow(VpnTransportProtocol.Tcp, 80), chrome),
        )
        assertEquals(
            VpnTransportAction.DropChromeDirectHttps,
            policy.decide(flow(VpnTransportProtocol.Udp, 443), chrome),
        )
    }

    private fun flow(
        protocol: VpnTransportProtocol,
        port: Int = 443,
        address: String = "203.0.113.8",
    ) = VpnFlowTuple(
        protocol,
        InetSocketAddress(InetAddress.getByName("10.8.0.2"), 42_000),
        InetSocketAddress(InetAddress.getByName(address), port),
    )

    private fun address(
        host: String,
        port: Int,
    ) = InetSocketAddress(InetAddress.getByName(host), port)
}
