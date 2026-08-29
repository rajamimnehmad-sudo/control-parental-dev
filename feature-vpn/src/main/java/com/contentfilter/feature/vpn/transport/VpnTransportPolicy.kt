package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import com.contentfilter.feature.vpn.service.VpnTransportProtocol

internal enum class VpnTransportAction {
    ExistingDnsPath,
    AuthorizedChromeProxyPath,
    ForwardToHev,
    DropChromeDirectHttps,
    DropUnknownOwner,
    DropUnapprovedDestination,
}

internal class VpnTransportPolicy(
    private val chromePackage: String,
    private val destinationAuthority: VpnDestinationAuthority,
    private val proxyAddress: String = "127.0.0.1",
    private val proxyPort: Int = 8877,
) {
    fun decide(
        flow: VpnFlowTuple,
        owner: VpnConnectionOwnerResult,
    ): VpnTransportAction {
        if (flow.remoteAddress.port == DnsPort) return VpnTransportAction.ExistingDnsPath
        if (owner !is VpnConnectionOwnerResult.Resolved || owner.packages.isEmpty()) {
            return VpnTransportAction.DropUnknownOwner
        }
        if (chromePackage in owner.packages) {
            val normalized = flow.remoteAddress.address.hostAddress.orEmpty().substringBefore('%')
            if (
                flow.protocol == VpnTransportProtocol.Tcp &&
                normalized == proxyAddress &&
                flow.remoteAddress.port == proxyPort
            ) {
                return VpnTransportAction.AuthorizedChromeProxyPath
            }
            return VpnTransportAction.DropChromeDirectHttps
        }
        if (!destinationAuthority.isAllowed(flow.remoteAddress)) return VpnTransportAction.DropUnapprovedDestination
        return VpnTransportAction.ForwardToHev
    }

    private companion object {
        const val DnsPort = 53
    }
}
