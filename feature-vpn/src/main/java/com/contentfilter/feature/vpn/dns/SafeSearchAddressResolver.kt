package com.contentfilter.feature.vpn.dns

import java.net.InetAddress

class SafeSearchAddressResolver(
    private val lookup: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() },
) {
    fun supports(queryType: Int): Boolean = queryType == DNS_TYPE_A || queryType == DNS_TYPE_AAAA

    fun resolve(
        target: String,
        queryType: Int,
    ): List<ByteArray> {
        val expectedSize =
            when (queryType) {
                DNS_TYPE_A -> IPV4_ADDRESS_SIZE
                DNS_TYPE_AAAA -> IPV6_ADDRESS_SIZE
                else -> return emptyList()
            }
        return lookup(target)
            .map(InetAddress::getAddress)
            .filter { it.size == expectedSize }
            .distinctBy(ByteArray::contentHashCode)
    }

    private companion object {
        const val DNS_TYPE_A = 1
        const val DNS_TYPE_AAAA = 28
        const val IPV4_ADDRESS_SIZE = 4
        const val IPV6_ADDRESS_SIZE = 16
    }
}
