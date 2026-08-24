package com.contentfilter.feature.vpn.transport

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class Socks5SessionCredentials private constructor(
    private val usernameBytes: ByteArray,
    private val passwordBytes: ByteArray,
) : Closeable {
    fun username(): ByteArray = usernameBytes.copyOf()

    fun password(): ByteArray = passwordBytes.copyOf()

    fun usernameText(): String = usernameBytes.toString(Charsets.US_ASCII)

    fun passwordText(): String = passwordBytes.toString(Charsets.US_ASCII)

    override fun close() {
        usernameBytes.fill(0)
        passwordBytes.fill(0)
    }

    companion object {
        fun create(random: SecureRandom = SecureRandom()): Socks5SessionCredentials {
            fun secret(): ByteArray =
                ByteArray(24).also(random::nextBytes).let { randomBytes ->
                    Base64.getUrlEncoder().withoutPadding().encode(randomBytes).also { randomBytes.fill(0) }
                }
            return Socks5SessionCredentials(secret(), secret())
        }
    }
}

internal data class VpnLocalSocksMetrics(
    val acceptedConnections: Long,
    val rejectedDestinations: Long,
    val tcpConnects: Long,
    val udpAssociations: Long,
    val udpDatagrams: Long,
    val malformedUdpDatagrams: Long,
    val activeSessions: Int,
)

/** Loopback-only authenticated SOCKS5 server for the bounded 09A route set. */
internal class VpnLocalSocks5Server(
    private val protectedSockets: VpnProtectedSocketFactory,
    allowedAddresses: Collection<String>,
    private val credentials: Socks5SessionCredentials = Socks5SessionCredentials.create(),
    private val maximumSessions: Int = DefaultMaximumSessions,
    private val allowedPorts: Set<Int> = DefaultAllowedPorts,
) : Closeable {
    private val allowedAddresses = allowedAddresses.mapTo(hashSetOf()) { it.substringBefore('%') }
    private val running = AtomicBoolean(false)
    private val acceptedConnections = AtomicLong(0)
    private val rejectedDestinations = AtomicLong(0)
    private val tcpConnects = AtomicLong(0)
    private val udpAssociations = AtomicLong(0)
    private val udpDatagrams = AtomicLong(0)
    private val malformedUdpDatagrams = AtomicLong(0)
    private val sessions = Collections.synchronizedSet(mutableSetOf<Closeable>())
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var sessionExecutor: ExecutorService? = null

    val port: Int
        get() = requireNotNull(serverSocket).localPort

    fun username(): String = credentials.usernameText()

    fun password(): String = credentials.passwordText()

    fun start() {
        check(running.compareAndSet(false, true)) { "SOCKS5 server already started" }
        val socket = ServerSocket()
        socket.reuseAddress = false
        socket.bind(InetSocketAddress(InetAddress.getByName(LoopbackIpv4), 0), maximumSessions)
        serverSocket = socket
        val workers = Executors.newFixedThreadPool(maximumSessions) { task -> Thread(task, "GloshSocksSession09A") }
        sessionExecutor = workers
        acceptExecutor =
            Executors.newSingleThreadExecutor { task -> Thread(task, "GloshSocksAccept09A") }.also { executor ->
                executor.execute {
                    while (running.get()) {
                        val client = runCatching { socket.accept() }.getOrNull() ?: break
                        if (!client.inetAddress.isLoopbackAddress || sessions.size >= maximumSessions) {
                            runCatching { client.close() }
                            continue
                        }
                        sessions += client
                        acceptedConnections.incrementAndGet()
                        workers.execute {
                            try {
                                handleClient(client)
                            } finally {
                                sessions -= client
                                runCatching { client.close() }
                            }
                        }
                    }
                }
            }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = ControlTimeoutMillis
        val input = client.getInputStream()
        val output = client.getOutputStream()
        val username = credentials.username()
        val password = credentials.password()
        try {
            if (!Socks5Protocol.negotiateMethod(input, output)) return
            if (!Socks5Protocol.authenticate(input, output, username, password)) return
            val request = Socks5Protocol.readRequest(input)
            if (request == null) {
                Socks5Protocol.writeReply(output, Socks5Protocol.AddressTypeNotSupported)
                return
            }
            when (request.command) {
                Socks5Command.Connect -> {
                    if (!isAllowed(request.target)) {
                        rejectedDestinations.incrementAndGet()
                        Socks5Protocol.writeReply(output, Socks5Protocol.GeneralFailure)
                        return
                    }
                    handleConnect(client, request.target)
                }
                Socks5Command.UdpAssociate -> handleUdpAssociate(client)
            }
        } finally {
            username.fill(0)
            password.fill(0)
        }
    }

    private fun handleConnect(
        client: Socket,
        target: InetSocketAddress,
    ) {
        val upstream = protectedSockets.connectTcp(target)
        if (upstream == null) {
            Socks5Protocol.writeReply(client.getOutputStream(), Socks5Protocol.GeneralFailure)
            return
        }
        upstream.soTimeout = RelayReadTimeoutMillis
        sessions += upstream
        try {
            tcpConnects.incrementAndGet()
            Socks5Protocol.writeReply(
                client.getOutputStream(),
                Socks5Protocol.Success,
                upstream.localSocketAddress as InetSocketAddress,
            )
            client.soTimeout = 0
            val reverse = Thread({ relay(upstream, client) }, "GloshSocksTcpReverse09A")
            reverse.start()
            relay(client, upstream)
            runCatching { upstream.shutdownOutput() }
            reverse.join(RelayJoinTimeoutMillis)
        } finally {
            sessions -= upstream
            runCatching { upstream.close() }
        }
    }

    private fun relay(
        source: Socket,
        destination: Socket,
    ) {
        val buffer = ByteArray(TcpBufferSize)
        runCatching {
            while (running.get()) {
                val length = source.getInputStream().read(buffer)
                if (length < 0) break
                destination.getOutputStream().write(buffer, 0, length)
                destination.getOutputStream().flush()
            }
        }
    }

    private fun handleUdpAssociate(control: Socket) {
        val relay = DatagramSocket(InetSocketAddress(InetAddress.getByName(LoopbackIpv4), 0))
        val upstream = protectedSockets.openUdp()
        if (upstream == null) {
            relay.close()
            Socks5Protocol.writeReply(control.getOutputStream(), Socks5Protocol.GeneralFailure)
            return
        }
        relay.soTimeout = UdpPollMillis
        upstream.socket.soTimeout = UdpResponseTimeoutMillis
        sessions += relay
        sessions += upstream.socket
        udpAssociations.incrementAndGet()
        val associationOpen = AtomicBoolean(true)
        val controlWatcher =
            Thread(
                {
                    runCatching { while (control.getInputStream().read() >= 0) Unit }
                    associationOpen.set(false)
                    relay.close()
                    upstream.socket.close()
                },
                "GloshSocksUdpControl09A",
            )
        try {
            Socks5Protocol.writeReply(
                control.getOutputStream(),
                Socks5Protocol.Success,
                InetSocketAddress(InetAddress.getByName(LoopbackIpv4), relay.localPort),
            )
            control.soTimeout = 0
            controlWatcher.start()
            var clientEndpoint: InetSocketAddress? = null
            val incomingBuffer = ByteArray(MaximumUdpDatagramSize)
            val responseBuffer = ByteArray(MaximumUdpDatagramSize)
            while (running.get() && associationOpen.get()) {
                val incoming = DatagramPacket(incomingBuffer, incomingBuffer.size)
                try {
                    relay.receive(incoming)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val source = incoming.socketAddress as? InetSocketAddress ?: continue
                if (!source.address.isLoopbackAddress || (clientEndpoint != null && source != clientEndpoint)) continue
                clientEndpoint = source
                val datagram = Socks5Protocol.parseUdpDatagram(incoming.data, incoming.length)
                if (datagram == null || !isAllowed(datagram.target)) {
                    malformedUdpDatagrams.incrementAndGet()
                    continue
                }
                upstream.send(DatagramPacket(datagram.payload, datagram.payload.size, datagram.target))
                udpDatagrams.incrementAndGet()
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                try {
                    upstream.socket.receive(response)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val responseSource = response.socketAddress as? InetSocketAddress ?: continue
                if (!isAllowed(responseSource)) continue
                val wrapped = Socks5Protocol.encodeUdpDatagram(responseSource, response.data, response.length)
                relay.send(DatagramPacket(wrapped, wrapped.size, clientEndpoint))
            }
        } finally {
            associationOpen.set(false)
            sessions -= relay
            sessions -= upstream.socket
            relay.close()
            upstream.socket.close()
            controlWatcher.join(ControlWatcherJoinMillis)
        }
    }

    private fun isAllowed(target: InetSocketAddress): Boolean =
        target.address.hostAddress.orEmpty().substringBefore('%') in allowedAddresses && target.port in allowedPorts

    fun metrics(): VpnLocalSocksMetrics =
        VpnLocalSocksMetrics(
            acceptedConnections = acceptedConnections.get(),
            rejectedDestinations = rejectedDestinations.get(),
            tcpConnects = tcpConnects.get(),
            udpAssociations = udpAssociations.get(),
            udpDatagrams = udpDatagrams.get(),
            malformedUdpDatagrams = malformedUdpDatagrams.get(),
            activeSessions = sessions.size,
        )

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        synchronized(sessions) { sessions.toList() }.forEach { runCatching { it.close() } }
        sessions.clear()
        acceptExecutor?.shutdownNow()
        sessionExecutor?.shutdownNow()
        acceptExecutor?.awaitTermination(ExecutorShutdownMillis, TimeUnit.MILLISECONDS)
        sessionExecutor?.awaitTermination(ExecutorShutdownMillis, TimeUnit.MILLISECONDS)
        credentials.close()
        serverSocket = null
        acceptExecutor = null
        sessionExecutor = null
    }

    private companion object {
        const val LoopbackIpv4 = "127.0.0.1"
        const val DefaultMaximumSessions = 8
        const val ControlTimeoutMillis = 5_000
        const val RelayReadTimeoutMillis = 30_000
        const val RelayJoinTimeoutMillis = 2_000L
        const val ControlWatcherJoinMillis = 1_000L
        const val ExecutorShutdownMillis = 2_000L
        const val UdpPollMillis = 250
        const val UdpResponseTimeoutMillis = 2_000
        const val TcpBufferSize = 16 * 1024
        const val MaximumUdpDatagramSize = 65_535
        val DefaultAllowedPorts = setOf(80, 443)
    }
}
