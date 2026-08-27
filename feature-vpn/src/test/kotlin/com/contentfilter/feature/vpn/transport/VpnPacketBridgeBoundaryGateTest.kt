package com.contentfilter.feature.vpn.transport

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnPacketBridgeBoundaryGateTest {
    @Test
    fun `bounded packet gate rejects empty and oversized packets and always closes bridge`() {
        val bridge = RecordingBridge()
        val accepted =
            VpnPacketBridgeBoundaryGate.verify(
                factory = VpnPacketBridgeFactory { bridge },
                packets = listOf(ByteArray(1), ByteArray(1_500)),
                maximumPacketSize = 1_500,
            )
        assertTrue(accepted)
        assertTrue(bridge.closed.get())

        assertFalse(
            VpnPacketBridgeBoundaryGate.verify(
                factory = VpnPacketBridgeFactory { RecordingBridge() },
                packets = listOf(ByteArray(0)),
                maximumPacketSize = 1_500,
            ),
        )
        assertFalse(
            VpnPacketBridgeBoundaryGate.verify(
                factory = VpnPacketBridgeFactory { RecordingBridge() },
                packets = listOf(ByteArray(1_501)),
                maximumPacketSize = 1_500,
            ),
        )
    }

    private class RecordingBridge : VpnPacketBridge {
        override val type = PacketSocketType.SeqPacket
        override val engineFd = 42
        val closed = AtomicBoolean(false)

        override fun writePacket(packet: ByteArray): Boolean = !closed.get()

        override fun readPacket(buffer: ByteArray): Int = -1

        override fun close() {
            closed.set(true)
        }
    }
}
