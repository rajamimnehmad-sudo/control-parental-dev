package com.contentfilter.feature.vpn.transport

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/** Sole owner of reads from the Android TUN and serialized writes back to it. */
internal class VpnPacketDispatcher(
    interfaceDescriptor: ParcelFileDescriptor,
    private val maximumPacketSize: Int = DefaultMaximumPacketSize,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val input = FileInputStream(interfaceDescriptor.fileDescriptor)
    private val output = FileOutputStream(interfaceDescriptor.fileDescriptor)
    private val outputLock = Any()

    suspend fun readLoop(onPacket: suspend (ByteArray, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(maximumPacketSize)
            while (coroutineContext.isActive && !closed.get()) {
                val length = runCatching { input.read(buffer) }.getOrDefault(-1)
                if (length < 0) break
                if (length > 0) onPacket(buffer, length)
            }
        }

    fun writePacket(packet: ByteArray): Boolean =
        if (closed.get()) {
            false
        } else {
            runCatching {
                synchronized(outputLock) { output.write(packet) }
                true
            }.getOrDefault(false)
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
        runCatching { output.close() }
    }

    private companion object {
        const val DefaultMaximumPacketSize = 32 * 1024
    }
}
