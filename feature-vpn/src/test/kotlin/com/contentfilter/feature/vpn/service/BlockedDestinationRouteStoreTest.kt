package com.contentfilter.feature.vpn.service

import com.contentfilter.feature.vpn.dns.DnsAnswerAddresses
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedDestinationRouteStoreTest {
    @Test
    fun `new addresses stay routed until policy invalidates them`() {
        val store = BlockedDestinationRouteStore()
        val answers = answers("192.0.2.10", ttlMillis = 200)

        assertTrue(store.beginPreparation("example.com"))
        assertFalse(store.beginPreparation("example.com"))
        assertTrue(store.add("example.com", answers))
        assertFalse(store.add("example.com", answers))
        assertEquals(listOf("192.0.2.10"), store.activeRoutes().map { it.hostAddress.orEmpty() })
        store.clear()
        assertTrue(store.beginPreparation("example.com"))
        assertTrue(store.activeRoutes().isEmpty())
    }

    @Test
    fun `route count is bounded and keeps newest entries`() {
        val store = BlockedDestinationRouteStore(maxRoutes = 2)

        assertTrue(store.beginPreparation("example.com"))
        assertTrue(store.add("example.com", answers("192.0.2.1", "192.0.2.2", "192.0.2.3")))

        assertEquals(
            setOf("192.0.2.2", "192.0.2.3"),
            store.activeRoutes().map { it.hostAddress.orEmpty() }.toSet(),
        )
    }

    @Test
    fun `failed preparation can be retried`() {
        val store = BlockedDestinationRouteStore()

        assertTrue(store.beginPreparation("example.com"))
        store.forgetDomain("example.com")

        assertTrue(store.beginPreparation("example.com"))
    }

    private fun answers(
        vararg addresses: String,
        ttlMillis: Long = 500,
    ): DnsAnswerAddresses =
        DnsAnswerAddresses(
            addresses = addresses.map(InetAddress::getByName).toSet(),
            ttlMillis = ttlMillis,
        )
}
