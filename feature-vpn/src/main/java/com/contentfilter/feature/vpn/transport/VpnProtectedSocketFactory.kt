package com.contentfilter.feature.vpn.transport

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

internal data class VpnProtectedSocketMetrics(
    val protectFailures: Long,
    val tcpConnectAttempts: Long,
    val udpSendAttempts: Long,
    val protectedUdpSocketsCreated: Long,
    val protectUdpSuccess: Long,
    val protectUdpFailure: Long,
)

internal class VpnProtectedTcpSocket(
    val socket: Socket,
    private val resource: Closeable,
) : Closeable {
    override fun close() {
        runCatching { socket.close() }
        resource.close()
    }
}

internal class VpnProtectedDatagramSocket(
    val socket: DatagramSocket,
    private val onSend: () -> Unit,
    private val resource: Closeable,
) : Closeable {
    fun send(packet: DatagramPacket) {
        onSend()
        socket.send(packet)
    }

    override fun close() {
        runCatching { socket.close() }
        resource.close()
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
    private val resources: VpnOwnedResourceTracker = VpnOwnedResourceTracker(),
) {
    private val protectFailures = AtomicLong(0)
    private val tcpConnectAttempts = AtomicLong(0)
    private val udpSendAttempts = AtomicLong(0)
    private val protectedUdpSocketsCreated = AtomicLong(0)
    private val protectUdpSuccess = AtomicLong(0)
    private val protectUdpFailure = AtomicLong(0)

    fun connectTcp(target: InetSocketAddress): VpnProtectedTcpSocket? {
        val socket = tcpSocketFactory()
        val resource = resources.acquire(VpnOwnedResourceKind.ProtectedTcp)
        if (!runCatching { protectTcp(socket) }.getOrDefault(false)) {
            protectFailures.incrementAndGet()
            runCatching { socket.close() }
            resource.close()
            return null
        }
        val protectedSocket = VpnProtectedTcpSocket(socket, resource)
        return runCatching {
            tcpConnectAttempts.incrementAndGet()
            tcpConnector(socket, target)
            protectedSocket
        }.getOrElse {
            protectedSocket.close()
            null
        }
    }

    fun openUdp(): VpnProtectedDatagramSocket? {
        val socket = udpSocketFactory()
        protectedUdpSocketsCreated.incrementAndGet()
        val resource = resources.acquire(VpnOwnedResourceKind.ProtectedUdp)
        if (!runCatching { protectUdp(socket) }.getOrDefault(false)) {
            protectFailures.incrementAndGet()
            protectUdpFailure.incrementAndGet()
            runCatching { socket.close() }
            resource.close()
            return null
        }
        protectUdpSuccess.incrementAndGet()
        return VpnProtectedDatagramSocket(
            socket = socket,
            onSend = { udpSendAttempts.incrementAndGet() },
            resource = resource,
        )
    }

    fun metrics(): VpnProtectedSocketMetrics =
        VpnProtectedSocketMetrics(
            protectFailures = protectFailures.get(),
            tcpConnectAttempts = tcpConnectAttempts.get(),
            udpSendAttempts = udpSendAttempts.get(),
            protectedUdpSocketsCreated = protectedUdpSocketsCreated.get(),
            protectUdpSuccess = protectUdpSuccess.get(),
            protectUdpFailure = protectUdpFailure.get(),
        )
}
