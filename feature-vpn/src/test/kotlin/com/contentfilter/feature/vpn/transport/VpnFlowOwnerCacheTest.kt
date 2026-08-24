package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import com.contentfilter.feature.vpn.service.VpnTransportProtocol
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class VpnFlowOwnerCacheTest {
    @Test
    fun `same flow is single flight and cached`() {
        val calls = AtomicInteger(0)
        val release = CountDownLatch(1)
        val cache =
            VpnFlowOwnerCache(
                lookup = {
                    calls.incrementAndGet()
                    release.await(1, TimeUnit.SECONDS)
                    resolved()
                },
                nowMillis = { 10L },
            )
        val executor = Executors.newFixedThreadPool(4)
        val futures = List(4) { executor.submit<VpnConnectionOwnerResult> { cache.resolve(flow(), 1) } }
        release.countDown()

        futures.forEach { assertEquals(resolved(), it.get(1, TimeUnit.SECONDS)) }
        assertEquals(1, calls.get())
        assertEquals(resolved(), cache.resolve(flow(), 1))
        assertEquals(1, calls.get())
        executor.shutdownNow()
    }

    @Test
    fun `TTL and generation invalidate authority`() {
        var now = 0L
        val calls = AtomicInteger(0)
        val cache =
            VpnFlowOwnerCache(
                lookup = {
                    calls.incrementAndGet()
                    resolved()
                },
                nowMillis = { now },
                ttlMillis = 10,
            )

        cache.resolve(flow(), 1)
        cache.resolve(flow(), 1)
        now = 11
        cache.resolve(flow(), 1)
        cache.resolve(flow(), 2)

        assertEquals(3, calls.get())
    }

    private fun flow() =
        VpnFlowTuple(
            VpnTransportProtocol.Tcp,
            InetSocketAddress(InetAddress.getByName("10.8.0.2"), 42_000),
            InetSocketAddress(InetAddress.getByName("203.0.113.8"), 443),
        )

    private fun resolved() = VpnConnectionOwnerResult.Resolved(10_262, listOf("com.sec.android.app.sbrowser"))
}
