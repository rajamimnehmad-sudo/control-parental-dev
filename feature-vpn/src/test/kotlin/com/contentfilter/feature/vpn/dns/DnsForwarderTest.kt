package com.contentfilter.feature.vpn.dns

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DnsForwarderTest {
    @Test
    fun `fast resolver wins without waiting for a stalled resolver`() =
        runBlocking {
            val stalledStarted = CompletableDeferred<Unit>()
            val stalledCancelled = CompletableDeferred<Unit>()
            val expected = byteArrayOf(4, 2)
            val forwarder =
                DnsForwarder(
                    protectSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, upstream ->
                            if (upstream.hostAddress == StalledResolver) {
                                stalledStarted.complete(Unit)
                                try {
                                    awaitCancellation()
                                } finally {
                                    stalledCancelled.complete(Unit)
                                }
                            } else {
                                delay(FastResolverDelayMillis)
                                expected
                            }
                        },
                    fallbackDnsServers = emptyList(),
                )

            val response =
                withTimeout(QueryTimeoutMillis) {
                    forwarder.forward(
                        payload = byteArrayOf(1),
                        upstreamServers = listOf(address(StalledResolver), address(FastResolver)),
                    )
                }

            assertContentEquals(expected, response)
            withTimeout(CancellationTimeoutMillis) { stalledCancelled.await() }
            assertTrue(stalledStarted.isCompleted)
        }

    @Test
    fun `duplicate upstream and fallback resolver is queried once`() =
        runBlocking {
            val calls = AtomicInteger(0)
            val resolver = address(FastResolver)
            val forwarder =
                DnsForwarder(
                    protectSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, _ ->
                            calls.incrementAndGet()
                            byteArrayOf(1)
                        },
                    fallbackDnsServers = listOf(FastResolver),
                )

            forwarder.forward(byteArrayOf(1), listOf(resolver, resolver))

            assertEquals(1, calls.get())
        }

    @Test
    fun `failed resolver does not cancel a valid resolver`() =
        runBlocking {
            val expected = byteArrayOf(7)
            val forwarder =
                DnsForwarder(
                    protectSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, upstream ->
                            if (upstream.hostAddress == StalledResolver) error("resolver failed")
                            expected
                        },
                    fallbackDnsServers = emptyList(),
                )

            val response =
                forwarder.forward(
                    payload = byteArrayOf(1),
                    upstreamServers = listOf(address(StalledResolver), address(FastResolver)),
                )

            assertContentEquals(expected, response)
        }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)

    private companion object {
        const val StalledResolver = "192.0.2.1"
        const val FastResolver = "192.0.2.2"
        const val FastResolverDelayMillis = 25L
        const val QueryTimeoutMillis = 500L
        const val CancellationTimeoutMillis = 500L
    }
}
