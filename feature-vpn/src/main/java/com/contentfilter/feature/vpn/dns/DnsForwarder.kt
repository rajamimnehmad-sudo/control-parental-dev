package com.contentfilter.feature.vpn.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Forwards allowed DNS payloads through protected sockets so they bypass the VPN tunnel.
 */
class DnsForwarder(
    private val protectSocket: (DatagramSocket) -> Boolean,
) {
    fun forward(
        payload: ByteArray,
        upstreamServers: List<InetAddress>,
    ): ByteArray? {
        upstreamServers.withFallbacks().forEach { upstream ->
            runCatching {
                DatagramSocket().use { socket ->
                    if (!protectSocket(socket)) return@runCatching null
                    socket.soTimeout = TimeoutMillis
                    socket.send(DatagramPacket(payload, payload.size, upstream, DnsPort))
                    val buffer = ByteArray(MaxDnsPayloadSize)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    response.data.copyOf(response.length)
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun List<InetAddress>.withFallbacks(): List<InetAddress> =
        (this + FallbackDnsServers.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() })
            .distinctBy { it.hostAddress }

    private companion object {
        const val DnsPort = 53
        const val MaxDnsPayloadSize = 4096
        const val TimeoutMillis = 900
        val FallbackDnsServers = listOf("1.1.1.1", "8.8.8.8")
    }
}
