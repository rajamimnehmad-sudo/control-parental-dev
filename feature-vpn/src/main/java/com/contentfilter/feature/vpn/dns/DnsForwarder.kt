package com.contentfilter.feature.vpn.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * Forwards allowed DNS payloads through protected sockets so they bypass the VPN tunnel.
 */
internal class DnsForwarder(
    protectSocket: (DatagramSocket) -> Boolean,
    private val resolver: DnsUpstreamResolver = SocketDnsUpstreamResolver(protectSocket),
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
                        async { runCatching { resolver.resolve(payload, upstream) }.getOrNull() }
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
    private val protectSocket: (DatagramSocket) -> Boolean,
) : DnsUpstreamResolver {
    override suspend fun resolve(
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
                            if (!protectSocket(socket)) return@runCatching null
                            socket.soTimeout = TimeoutMillis
                            socket.send(DatagramPacket(payload, payload.size, upstream, DnsPort))
                            val buffer = ByteArray(MaxDnsPayloadSize)
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            packet.data.copyOf(packet.length)
                        }
                    }.getOrNull()
                if (continuation.isActive) continuation.resume(response)
            }
        }

    private companion object {
        const val DnsPort = 53
        const val MaxDnsPayloadSize = 4096
        const val TimeoutMillis = 900
    }
}
