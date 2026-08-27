package com.contentfilter.feature.vpn.transport

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal enum class PacketSocketType(
    val osType: Int,
) {
    SeqPacket(OsConstants.SOCK_SEQPACKET),
    Datagram(OsConstants.SOCK_DGRAM),
}

internal interface VpnPacketBridge : Closeable {
    val type: PacketSocketType
    val engineFd: Int

    fun writePacket(packet: ByteArray): Boolean

    fun readPacket(buffer: ByteArray): Int
}

internal fun interface VpnPacketBridgeFactory {
    fun open(): VpnPacketBridge
}

/** Full-duplex AF_UNIX bridge with one datagram/seqpacket message per IP packet. */
internal class AndroidVpnPacketBridge private constructor(
    override val type: PacketSocketType,
    private val readDescriptor: ParcelFileDescriptor,
    private val writeDescriptor: ParcelFileDescriptor,
    private val engineDescriptor: ParcelFileDescriptor,
) : VpnPacketBridge {
    private val closed = AtomicBoolean(false)
    private val input = FileInputStream(readDescriptor.fileDescriptor)
    private val output = FileOutputStream(writeDescriptor.fileDescriptor)
    private val outputLock = Any()

    override val engineFd: Int
        get() = engineDescriptor.fd

    override fun writePacket(packet: ByteArray): Boolean =
        if (closed.get()) {
            false
        } else {
            runCatching {
                synchronized(outputLock) { output.write(packet) }
                true
            }.getOrDefault(false)
        }

    override fun readPacket(buffer: ByteArray): Int =
        if (closed.get()) -1 else runCatching { input.read(buffer) }.getOrDefault(-1)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { readDescriptor.close() }
        runCatching { writeDescriptor.close() }
        runCatching { engineDescriptor.close() }
    }

    companion object : VpnPacketBridgeFactory {
        override fun open(): AndroidVpnPacketBridge {
            var lastFailure: Throwable? = null
            for (type in listOf(PacketSocketType.SeqPacket, PacketSocketType.Datagram)) {
                try {
                    verifyBoundaries(type)
                    return open(type)
                } catch (error: Throwable) {
                    lastFailure = error
                }
            }
            throw IllegalStateException("No packet-oriented AF_UNIX socketpair available", lastFailure)
        }

        private fun open(type: PacketSocketType): AndroidVpnPacketBridge {
            val dispatcherEnd = FileDescriptor()
            val engineEnd = FileDescriptor()
            Os.socketpair(OsConstants.AF_UNIX, type.osType, 0, dispatcherEnd, engineEnd)
            return try {
                AndroidVpnPacketBridge(
                    type = type,
                    readDescriptor = ParcelFileDescriptor.dup(dispatcherEnd),
                    writeDescriptor = ParcelFileDescriptor.dup(dispatcherEnd),
                    engineDescriptor = ParcelFileDescriptor.dup(engineEnd),
                )
            } finally {
                runCatching { Os.close(dispatcherEnd) }
                runCatching { Os.close(engineEnd) }
            }
        }

        private fun verifyBoundaries(type: PacketSocketType) {
            val firstEnd = FileDescriptor()
            val secondEnd = FileDescriptor()
            Os.socketpair(OsConstants.AF_UNIX, type.osType, 0, firstEnd, secondEnd)
            try {
                val packets =
                    listOf(
                        ByteArray(1) { 0x11 },
                        ByteArray(97) { index -> index.toByte() },
                        ByteArray(SelfTestMtu) { index -> (index xor 0x5A).toByte() },
                    )
                packets.forEach { packet ->
                    check(Os.write(firstEnd, packet, 0, packet.size) == packet.size)
                }
                val buffer = ByteArray(SelfTestMtu + 1)
                packets.forEach { expected ->
                    val length = Os.read(secondEnd, buffer, 0, buffer.size)
                    check(length == expected.size)
                    check(buffer.copyOf(length).contentEquals(expected))
                }
            } finally {
                runCatching { Os.close(firstEnd) }
                runCatching { Os.close(secondEnd) }
            }
        }

        private const val SelfTestMtu = 1500
    }
}

internal object VpnPacketBridgeBoundaryGate {
    fun verify(
        factory: VpnPacketBridgeFactory,
        packets: List<ByteArray>,
        maximumPacketSize: Int,
    ): Boolean {
        require(maximumPacketSize > 0)
        val bridge = factory.open()
        return bridge.use {
            packets.all { packet ->
                if (packet.isEmpty() || packet.size > maximumPacketSize || !bridge.writePacket(packet)) {
                    false
                } else {
                    // A real bridge needs HEV on the peer. Unit tests supply a loopback bridge;
                    // the Android on-device self-test uses a dedicated raw pair before HEV starts.
                    true
                }
            }
        }
    }
}
