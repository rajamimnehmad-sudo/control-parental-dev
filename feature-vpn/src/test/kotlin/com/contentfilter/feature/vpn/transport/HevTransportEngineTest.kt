package com.contentfilter.feature.vpn.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(0, resources.snapshot().ownedFdResources)
        assertEquals(3, resources.snapshot().ownedFdResourcesPeak)
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
}
