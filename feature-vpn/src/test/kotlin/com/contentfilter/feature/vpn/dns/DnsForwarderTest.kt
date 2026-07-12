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
            val query = query()
            val expected = response(query)
            val forwarder =
                DnsForwarder(
                    protectDatagramSocket = { true },
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
                        payload = query,
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
            val query = query()
            val forwarder =
                DnsForwarder(
                    protectDatagramSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, _ ->
                            calls.incrementAndGet()
                            response(query)
                        },
                    fallbackDnsServers = listOf(FastResolver),
                )

            forwarder.forward(query, listOf(resolver, resolver))

            assertEquals(1, calls.get())
        }

    @Test
    fun `failed resolver does not cancel a valid resolver`() =
        runBlocking {
            val query = query()
            val expected = response(query)
            val forwarder =
                DnsForwarder(
                    protectDatagramSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, upstream ->
                            if (upstream.hostAddress == StalledResolver) error("resolver failed")
                            expected
                        },
                    fallbackDnsServers = emptyList(),
                )

            val response =
                forwarder.forward(
                    payload = query,
                    upstreamServers = listOf(address(StalledResolver), address(FastResolver)),
                )

            assertContentEquals(expected, response)
        }

    @Test
    fun `SERVFAIL response does not win over a valid answer`() =
        runBlocking {
            val query = query()
            val expected = response(query)
            val forwarder =
                DnsForwarder(
                    protectDatagramSocket = { true },
                    resolver =
                        DnsUpstreamResolver { _, upstream ->
                            if (upstream.hostAddress == StalledResolver) {
                                response(query, responseCode = ServFail)
                            } else {
                                delay(FastResolverDelayMillis)
                                expected
                            }
                        },
                    fallbackDnsServers = emptyList(),
                )

            val actual =
                forwarder.forward(
                    payload = query,
                    upstreamServers = listOf(address(StalledResolver), address(FastResolver)),
                )

            assertContentEquals(expected, actual)
        }

    @Test
    fun `response with a different transaction id is rejected`() =
        runBlocking {
            val query = query()
            val forwarder =
                DnsForwarder(
                    protectDatagramSocket = { true },
                    resolver = DnsUpstreamResolver { _, _ -> response(query, transactionId = OtherTransactionId) },
                    fallbackDnsServers = emptyList(),
                )

            val actual = forwarder.forward(query, listOf(address(FastResolver)))

            assertEquals(null, actual)
        }

    @Test
    fun `truncated UDP response is not usable as a final answer`() {
        val query = query()
        val truncated = response(query, truncated = true)

        assertTrue(DnsWireResponseValidator.isTruncated(query, truncated))
        assertTrue(!DnsWireResponseValidator.isUsable(query, truncated))
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)

    private fun query(transactionId: Int = TransactionId): ByteArray =
        ByteArray(DnsHeaderSize).apply {
            this[0] = (transactionId ushr 8).toByte()
            this[1] = transactionId.toByte()
            this[2] = RecursionDesired.toByte()
            this[5] = 1
        }

    private fun response(
        query: ByteArray,
        responseCode: Int = NoError,
        truncated: Boolean = false,
        transactionId: Int = TransactionId,
    ): ByteArray =
        query.copyOf().apply {
            this[0] = (transactionId ushr 8).toByte()
            this[1] = transactionId.toByte()
            this[2] = (ResponseFlagsHigh or if (truncated) TruncatedFlag else 0).toByte()
            this[3] = (RecursionAvailable or responseCode).toByte()
        }

    private companion object {
        const val StalledResolver = "192.0.2.1"
        const val FastResolver = "192.0.2.2"
        const val FastResolverDelayMillis = 25L
        const val QueryTimeoutMillis = 500L
        const val CancellationTimeoutMillis = 500L
        const val DnsHeaderSize = 12
        const val TransactionId = 0x1234
        const val OtherTransactionId = 0x4321
        const val RecursionDesired = 0x01
        const val ResponseFlagsHigh = 0x81
        const val RecursionAvailable = 0x80
        const val TruncatedFlag = 0x02
        const val NoError = 0
        const val ServFail = 2
    }
}
