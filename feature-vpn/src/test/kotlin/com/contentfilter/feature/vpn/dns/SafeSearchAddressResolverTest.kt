package com.contentfilter.feature.vpn.dns

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SafeSearchAddressResolverTest {
    private val ipv4 = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
    private val ipv6 = InetAddress.getByAddress(ByteArray(16) { it.toByte() })
    private val resolver = SafeSearchAddressResolver { listOf(ipv4, ipv6) }

    @Test
    fun `A query returns only IPv4 target addresses`() {
        val addresses = resolver.resolve("safe.example", queryType = 1)

        assertEquals(1, addresses.size)
        assertContentEquals(ipv4.address, addresses.single())
    }

    @Test
    fun `AAAA query returns only IPv6 target addresses`() {
        val addresses = resolver.resolve("safe.example", queryType = 28)

        assertEquals(1, addresses.size)
        assertContentEquals(ipv6.address, addresses.single())
    }

    @Test
    fun `HTTPS query does not trigger address lookup`() {
        var lookupCalled = false
        val resolver =
            SafeSearchAddressResolver {
                lookupCalled = true
                emptyList()
            }

        assertEquals(emptyList(), resolver.resolve("safe.example", queryType = 65))
        assertEquals(false, lookupCalled)
    }
}
