package com.contentfilter.feature.vpn.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Forwards allowed DNS payloads through protected sockets so they bypass the VPN tunnel.
 */
internal class DnsForwarder(
    protectDatagramSocket: (DatagramSocket) -> Boolean,
    protectTcpSocket: (Socket) -> Boolean = { true },
    private val resolver: DnsUpstreamResolver =
        SocketDnsUpstreamResolver(
            protectDatagramSocket = protectDatagramSocket,
            protectTcpSocket = protectTcpSocket,
        ),
    private val fallbackDnsServers: List<String> = DefaultFallbackDnsServers,
) {
    suspend fun forward(
        payload: ByteArray,
        upstreamServers: List<InetAddress>,
    ): ByteArray? =
        supervisorScope {
            val attempts =
                upstreamServers
                    .withFallbacks()
                    .map { upstream ->
                        async {
                            runCatching { resolver.resolve(payload, upstream) }
                                .getOrNull()
                                ?.takeIf { response -> DnsWireResponseValidator.isUsable(payload, response) }
                        }
                    }
                    .toMutableList()
            try {
                while (attempts.isNotEmpty()) {
                    val completed =
                        select {
                            attempts.forEach { attempt ->
                                attempt.onAwait { response -> attempt to response }
                            }
                        }
                    attempts.remove(completed.first)
                    completed.second?.let { return@supervisorScope it }
                }
                null
            } finally {
                attempts.forEach { it.cancel() }
            }
        }

    private fun List<InetAddress>.withFallbacks(): List<InetAddress> =
        (this + fallbackDnsServers.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() })
            .distinctBy { it.hostAddress }

    companion object {
        val DefaultFallbackDnsServers = listOf("1.1.1.1", "8.8.8.8")
    }
}

internal fun interface DnsUpstreamResolver {
    suspend fun resolve(
        payload: ByteArray,
        upstream: InetAddress,
    ): ByteArray?
}

private class SocketDnsUpstreamResolver(
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    private val protectTcpSocket: (Socket) -> Boolean,
) : DnsUpstreamResolver {
    override suspend fun resolve(
        payload: ByteArray,
        upstream: InetAddress,
    ): ByteArray? {
        val udpResponse = resolveUdp(payload, upstream)
        return if (udpResponse != null && DnsWireResponseValidator.isTruncated(payload, udpResponse)) {
            resolveTcp(payload, upstream)
        } else {
            udpResponse
        }
    }

    private suspend fun resolveUdp(
        payload: ByteArray,
        upstream: InetAddress,
    ): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val socket =
                runCatching { DatagramSocket() }
                    .getOrElse {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
            continuation.invokeOnCancellation { socket.close() }
            Dispatchers.IO.dispatch(continuation.context) {
                val response =
                    runCatching {
                        socket.use {
                            if (!protectDatagramSocket(socket)) return@runCatching null
                            socket.soTimeout = TimeoutMillis
                            socket.connect(upstream, DnsPort)
                            socket.send(DatagramPacket(payload, payload.size))
                            val buffer = ByteArray(MaxDnsPayloadSize)
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            packet.data.copyOf(packet.length)
                        }
                    }.getOrNull()
                if (continuation.isActive) continuation.resume(response)
            }
        }

    private suspend fun resolveTcp(
        payload: ByteArray,
        upstream: InetAddress,
    ): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val socket = Socket()
            continuation.invokeOnCancellation { socket.close() }
            Dispatchers.IO.dispatch(continuation.context) {
                val response =
                    runCatching {
                        socket.use {
                            if (!protectTcpSocket(socket)) return@runCatching null
                            socket.soTimeout = TimeoutMillis
                            socket.connect(InetSocketAddress(upstream, DnsPort), TimeoutMillis)
                            val output = DataOutputStream(socket.getOutputStream())
                            output.writeShort(payload.size)
                            output.write(payload)
                            output.flush()
                            val input = DataInputStream(socket.getInputStream())
                            val responseLength = input.readUnsignedShort()
                            if (responseLength !in MinDnsPayloadSize..MaxDnsPayloadSize) return@runCatching null
                            ByteArray(responseLength).also(input::readFully)
                        }
                    }.getOrNull()
                if (continuation.isActive) continuation.resume(response)
            }
        }

    private companion object {
        const val DnsPort = 53
        const val MinDnsPayloadSize = 12
        const val MaxDnsPayloadSize = 4096
        const val TimeoutMillis = 900
    }
}

internal object DnsWireResponseValidator {
    fun isUsable(
        query: ByteArray,
        response: ByteArray,
    ): Boolean {
        if (!hasMatchingHeader(query, response)) return false
        if ((response[FlagsHighOffset].toInt() and ResponseBit) == 0) return false
        if (isTruncated(query, response)) return false
        val responseCode = response[FlagsLowOffset].toInt() and ResponseCodeMask
        return responseCode == NoError || responseCode == NameError
    }

    fun isTruncated(
        query: ByteArray,
        response: ByteArray,
    ): Boolean =
        hasMatchingHeader(query, response) &&
            (response[FlagsHighOffset].toInt() and TruncatedBit) != 0

    private fun hasMatchingHeader(
        query: ByteArray,
        response: ByteArray,
    ): Boolean =
        query.size >= DnsHeaderSize &&
            response.size >= DnsHeaderSize &&
            query[TransactionIdHighOffset] == response[TransactionIdHighOffset] &&
            query[TransactionIdLowOffset] == response[TransactionIdLowOffset] &&
            (query[FlagsHighOffset].toInt() and OpcodeMask) ==
            (response[FlagsHighOffset].toInt() and OpcodeMask)

    private const val DnsHeaderSize = 12
    private const val TransactionIdHighOffset = 0
    private const val TransactionIdLowOffset = 1
    private const val FlagsHighOffset = 2
    private const val FlagsLowOffset = 3
    private const val ResponseBit = 0x80
    private const val TruncatedBit = 0x02
    private const val OpcodeMask = 0x78
    private const val ResponseCodeMask = 0x0F
    private const val NoError = 0
    private const val NameError = 3
}
