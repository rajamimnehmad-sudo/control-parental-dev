package com.contentfilter.user.chromedataplane

import java.net.InetAddress

internal class ChromePhotosRealWebRouteResolver(
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    private val maximumAddresses: Int = DefaultMaximumAddresses,
) {
    init {
        require(maximumAddresses > 0)
    }

    fun resolve(hosts: Collection<String>): Set<String> {
        require(hosts.isNotEmpty())
        val addresses = linkedSetOf<String>()
        hosts.map(::normalizeDnsHost).sorted().forEach { host ->
            val resolved = lookup(host).filter { address -> address.isPublicRouteAddress() }
            check(resolved.isNotEmpty()) { "No public addresses for authorized host" }
            resolved.forEach { address ->
                addresses += address.hostAddress.substringBefore('%')
                check(addresses.size <= maximumAddresses) { "Authorized route set exceeds bound" }
            }
        }
        return addresses
    }

    private fun InetAddress.isPublicRouteAddress(): Boolean =
        !isAnyLocalAddress &&
            !isLoopbackAddress &&
            !isLinkLocalAddress &&
            !isSiteLocalAddress &&
            !isMulticastAddress

    private companion object {
        const val DefaultMaximumAddresses = 32
    }
}
