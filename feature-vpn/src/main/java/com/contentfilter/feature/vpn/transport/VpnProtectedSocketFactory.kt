package com.contentfilter.feature.vpn.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

internal data class VpnProtectedSocketMetrics(
    val protectFailures: Long,
    val tcpConnectAttempts: Long,
    val udpSendAttempts: Long,
)

internal class VpnProtectedDatagramSocket(
    val socket: DatagramSocket,
    private val onSend: () -> Unit,
) {
    fun send(packet: DatagramPacket) {
        onSend()
        socket.send(packet)
    }
}

/** Enforces protect-before-connect/send for every transport socket. */
internal class VpnProtectedSocketFactory(
    private val protectTcp: (Socket) -> Boolean,
    private val protectUdp: (DatagramSocket) -> Boolean,
    private val tcpSocketFactory: () -> Socket = {
        Socket().apply { bind(InetSocketAddress(0)) }
    },
    private val udpSocketFactory: () -> DatagramSocket = {
        DatagramSocket(null).apply { bind(InetSocketAddress(0)) }
    },
    private val tcpConnector: (Socket, InetSocketAddress) -> Unit = { socket, target -> socket.connect(target) },
) {
    private val protectFailures = AtomicLong(0)
    private val tcpConnectAttempts = AtomicLong(0)
    private val udpSendAttempts = AtomicLong(0)

    fun connectTcp(target: InetSocketAddress): Socket? {
        val socket = tcpSocketFactory()
        if (!runCatching { protectTcp(socket) }.getOrDefault(false)) {
            protectFailures.incrementAndGet()
            runCatching { socket.close() }
            return null
        }
        return runCatching {
            tcpConnectAttempts.incrementAndGet()
            tcpConnector(socket, target)
            socket
        }.getOrElse {
            runCatching { socket.close() }
            null
        }
    }

    fun openUdp(): VpnProtectedDatagramSocket? {
        val socket = udpSocketFactory()
        if (!runCatching { protectUdp(socket) }.getOrDefault(false)) {
            protectFailures.incrementAndGet()
            runCatching { socket.close() }
            return null
        }
        return VpnProtectedDatagramSocket(socket) { udpSendAttempts.incrementAndGet() }
    }

    fun metrics(): VpnProtectedSocketMetrics =
        VpnProtectedSocketMetrics(
            protectFailures = protectFailures.get(),
            tcpConnectAttempts = tcpConnectAttempts.get(),
            udpSendAttempts = udpSendAttempts.get(),
        )
}
