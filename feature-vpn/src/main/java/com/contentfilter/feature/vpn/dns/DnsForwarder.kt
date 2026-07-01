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
        val upstream = upstreamServers.firstOrNull() ?: return null
        DatagramSocket().use { socket ->
            if (!protectSocket(socket)) return null
            socket.soTimeout = TimeoutMillis
            socket.send(DatagramPacket(payload, payload.size, upstream, DnsPort))
            val buffer = ByteArray(MaxDnsPayloadSize)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return response.data.copyOf(response.length)
        }
    }

    private companion object {
        const val DnsPort = 53
        const val MaxDnsPayloadSize = 4096
        const val TimeoutMillis = 1_500
    }
}
