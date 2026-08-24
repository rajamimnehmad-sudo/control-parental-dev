package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import com.contentfilter.feature.vpn.service.VpnTransportProtocol

internal enum class VpnTransportAction {
    ExistingDnsPath,
    ForwardToHev,
    DropChromeDirectHttps,
    DropUnknownOwner,
    DropUnapprovedDestination,
}

internal class VpnTransportPolicy(
    private val chromePackage: String,
    private val allowedDestinationAddresses: Set<String>,
) {
    internal fun allowedAddressesForStress(): Set<String> = allowedDestinationAddresses

    fun decide(
        flow: VpnFlowTuple,
        owner: VpnConnectionOwnerResult,
    ): VpnTransportAction {
        if (flow.remoteAddress.port == DnsPort) return VpnTransportAction.ExistingDnsPath
        if (flow.remoteAddress.address.hostAddress.orEmpty().substringBefore('%') !in allowedDestinationAddresses) {
            return VpnTransportAction.DropUnapprovedDestination
        }
        if (owner !is VpnConnectionOwnerResult.Resolved) return VpnTransportAction.DropUnknownOwner
        if (
            chromePackage in owner.packages &&
            flow.remoteAddress.port == HttpsPort &&
            flow.protocol in setOf(VpnTransportProtocol.Tcp, VpnTransportProtocol.Udp)
        ) {
            return VpnTransportAction.DropChromeDirectHttps
        }
        return VpnTransportAction.ForwardToHev
    }

    private companion object {
        const val DnsPort = 53
        const val HttpsPort = 443
    }
}
