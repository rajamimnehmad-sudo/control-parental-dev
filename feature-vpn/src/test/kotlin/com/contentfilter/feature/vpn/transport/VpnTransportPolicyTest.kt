package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import com.contentfilter.feature.vpn.service.VpnTransportProtocol
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class VpnTransportPolicyTest {
    private val policy = VpnTransportPolicy("com.android.chrome", setOf("203.0.113.8"))

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
    fun `DNS remains on existing pipeline and unapproved target never forwards`() {
        val other = VpnConnectionOwnerResult.Resolved(10_262, listOf("com.sec.android.app.sbrowser"))

        assertEquals(VpnTransportAction.ExistingDnsPath, policy.decide(flow(VpnTransportProtocol.Udp, 53), other))
        assertEquals(
            VpnTransportAction.DropUnapprovedDestination,
            policy.decide(flow(VpnTransportProtocol.Tcp, address = "198.51.100.4"), other),
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
}
