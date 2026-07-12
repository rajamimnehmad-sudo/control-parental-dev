package com.contentfilter.feature.vpn.dns

import com.contentfilter.core.domain.model.SearchEngineCatalog
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal data class DnsEnforcementRoute(
    val address: InetAddress,
    val prefixLength: Int,
)

internal object DnsEnforcementRoutePlanner {
    fun hostRoutes(
        upstreamDnsServers: List<InetAddress>,
        includeEncryptedDnsResolvers: Boolean,
    ): List<DnsEnforcementRoute> {
        val addresses =
            buildList {
                addAll(upstreamDnsServers)
                if (includeEncryptedDnsResolvers) {
                    SearchEngineCatalog.encryptedDnsResolverAddresses.forEach { address ->
                        runCatching { InetAddress.getByName(address) }.getOrNull()?.let(::add)
                    }
                }
            }
        return addresses
            .distinct()
            .mapNotNull { address ->
                when (address) {
                    is Inet4Address -> DnsEnforcementRoute(address, Ipv4HostPrefixLength)
                    is Inet6Address -> DnsEnforcementRoute(address, Ipv6HostPrefixLength)
                    else -> null
                }
            }
    }

    private const val Ipv4HostPrefixLength = 32
    private const val Ipv6HostPrefixLength = 128
}
