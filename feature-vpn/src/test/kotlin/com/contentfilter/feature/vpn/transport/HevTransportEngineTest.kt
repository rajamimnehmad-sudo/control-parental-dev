package com.contentfilter.feature.vpn.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HevTransportEngineTest {
    @Test
    fun `start quit join close and double stop are safe`() {
        val native = BlockingNative()
        val bridge = FakeBridge()
        val resources = VpnOwnedResourceTracker()
        val engine = HevTransportEngine(native, VpnPacketBridgeFactory { bridge }, resources)

        assertEquals(PacketSocketType.SeqPacket, engine.start(1234, "user", "pass") {})
        assertTrue(native.started.await(1, TimeUnit.SECONDS))
        assertTrue(engine.writePacket(byteArrayOf(1, 2, 3)))
        assertEquals(1, engine.packetWriteCount())

        assertTrue(engine.stop().joined)
        assertTrue(bridge.closed.get())
        assertTrue(engine.stop().joined)
        assertFalse(engine.isRunning())
        assertEquals(HevTransportLifecycleState.Stopped, engine.lifecycleSnapshot().state)
        assertEquals(1, engine.lifecycleSnapshot().cleanupCount)
        assertEquals(0, resources.snapshot().ownedFdResources)
        assertEquals(3, resources.snapshot().ownedFdResourcesPeak)
    }

    @Test
    fun `join timeout quarantines without closing bridge and late termination cleans exactly once`() {
        val native = DeferredNative()
        val bridge = FakeBridge()
        val resources = VpnOwnedResourceTracker()
        val engine =
            HevTransportEngine(
                nativeApi = native,
                bridgeFactory = VpnPacketBridgeFactory { bridge },
                resources = resources,
                nativeJoinTimeoutMillis = 20,
            )

        engine.start(1234, "user", "pass") {}
        assertTrue(native.started.await(1, TimeUnit.SECONDS))

        val firstStop = engine.stop()
        assertFalse(firstStop.joined)
        assertFalse(firstStop.cleanupComplete)
        assertEquals(HevTransportLifecycleState.Quarantined, firstStop.state)
        assertFalse(bridge.closed.get())
        assertEquals(3, resources.snapshot().ownedFdResources)
        assertFailsWith<IllegalStateException> { engine.start(1234, "user", "pass") {} }

        val secondStop = engine.stop()
        assertFalse(secondStop.joined)
        assertFalse(bridge.closed.get())

        native.release.countDown()
        assertTrue(waitUntil { engine.lifecycleSnapshot().state == HevTransportLifecycleState.Stopped })
        assertTrue(bridge.closed.get())
        assertEquals(1, engine.lifecycleSnapshot().cleanupCount)
        assertEquals(0, resources.snapshot().ownedFdResources)
        assertTrue(engine.stop().joined)
        assertEquals(1, engine.lifecycleSnapshot().cleanupCount)
    }

    @Test
    fun `clean stop permits start again`() {
        val native = CyclingNative()
        val resources = VpnOwnedResourceTracker()
        val bridges = mutableListOf<FakeBridge>()
        val engine =
            HevTransportEngine(
                nativeApi = native,
                bridgeFactory =
                    VpnPacketBridgeFactory {
                        FakeBridge().also(bridges::add)
                    },
                resources = resources,
            )

        repeat(2) {
            engine.start(1234, "user", "pass") {}
            assertTrue(engine.stop().joined)
        }

        assertEquals(2, native.runCount.get())
        assertEquals(2, bridges.size)
        assertTrue(bridges.all { it.closed.get() })
        assertEquals(2, engine.lifecycleSnapshot().cleanupCount)
        assertEquals(0, resources.snapshot().ownedFdResources)
    }

    @Test
    fun `native exit during initialization is rejected and cleaned`() {
        val bridge = FakeBridge()
        val resources = VpnOwnedResourceTracker()
        val engine =
            HevTransportEngine(
                nativeApi = ImmediateNative,
                bridgeFactory = VpnPacketBridgeFactory { bridge },
                resources = resources,
            )

        assertFailsWith<IllegalStateException> { engine.start(1234, "user", "pass") {} }

        assertEquals(HevTransportLifecycleState.Stopped, engine.lifecycleSnapshot().state)
        assertTrue(bridge.closed.get())
        assertEquals(0, resources.snapshot().ownedFdResources)
    }

    private class BlockingNative : HevNativeApi {
        val started = CountDownLatch(1)
        private val quit = CountDownLatch(1)

        override fun run(
            config: ByteArray,
            tunFd: Int,
        ): Int {
            started.countDown()
            quit.await(1, TimeUnit.SECONDS)
            return 0
        }

        override fun quit() {
            quit.countDown()
        }

        override fun stats() = HevNativeStats(0, 0, 0, 0)
    }

    private class DeferredNative : HevNativeApi {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun run(
            config: ByteArray,
            tunFd: Int,
        ): Int {
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            return 0
        }

        override fun quit() = Unit

        override fun stats() = HevNativeStats(0, 0, 0, 0)
    }

    private class CyclingNative : HevNativeApi {
        val runCount = AtomicInteger(0)
        private val quit = java.util.concurrent.atomic.AtomicReference<CountDownLatch>()

        override fun run(
            config: ByteArray,
            tunFd: Int,
        ): Int {
            runCount.incrementAndGet()
            val current = CountDownLatch(1)
            quit.set(current)
            current.await(1, TimeUnit.SECONDS)
            return 0
        }

        override fun quit() {
            quit.get()?.countDown()
        }

        override fun stats() = HevNativeStats(0, 0, 0, 0)
    }

    private object ImmediateNative : HevNativeApi {
        override fun run(
            config: ByteArray,
            tunFd: Int,
        ) = 0

        override fun quit() = Unit

        override fun stats() = HevNativeStats(0, 0, 0, 0)
    }

    private class FakeBridge : VpnPacketBridge {
        override val type = PacketSocketType.SeqPacket
        override val engineFd = 42
        val closed = AtomicBoolean(false)

        override fun writePacket(packet: ByteArray) = !closed.get()

        override fun readPacket(buffer: ByteArray) = -1

        override fun close() {
            closed.set(true)
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
