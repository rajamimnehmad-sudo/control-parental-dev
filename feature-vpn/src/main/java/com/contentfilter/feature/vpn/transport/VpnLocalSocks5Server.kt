package com.contentfilter.feature.vpn.transport

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    val udpDatagramsOut: Long,
    val udpDatagramsIn: Long,
    val malformedUdpDatagrams: Long,
    val udpFragmentsDropped: Long,
    val activeSessions: Int,
    val activeUdpAssociations: Int,
    val activeUdpAssociationsPeak: Int,
    val malformedProbeEmptySent: Long,
    val malformedProbeTruncatedSent: Long,
    val malformedProbeInvalidHeaderSent: Long,
    val sessionIoFailures: Long,
    val executorShutdownTimeouts: Long,
)

internal data class VpnLocalSocksCloseResult(
    val acceptExecutorTerminated: Boolean,
    val sessionExecutorTerminated: Boolean,
) {
    val clean: Boolean = acceptExecutorTerminated && sessionExecutorTerminated
}

/** Loopback-only authenticated SOCKS5 server for the bounded 09A route set. */
internal class VpnLocalSocks5Server(
    private val protectedSockets: VpnProtectedSocketFactory,
    allowedAddresses: Collection<String>,
    private val credentials: Socks5SessionCredentials = Socks5SessionCredentials.create(),
    private val maximumSessions: Int = DefaultMaximumSessions,
    private val allowedPorts: Set<Int> = DefaultAllowedPorts,
    private val resources: VpnOwnedResourceTracker = VpnOwnedResourceTracker(),
    private val malformedResponseProbeEnabled: Boolean = false,
    transportScope: VpnTransportScope = VpnTransportScope.Controlled,
) : Closeable {
    private val destinationAuthority = VpnDestinationAuthority(allowedAddresses, allowedPorts, transportScope)
    private val running = AtomicBoolean(false)
    private val acceptedConnections = AtomicLong(0)
    private val rejectedDestinations = AtomicLong(0)
    private val tcpConnects = AtomicLong(0)
    private val udpAssociations = AtomicLong(0)
    private val udpDatagramsOut = AtomicLong(0)
    private val udpDatagramsIn = AtomicLong(0)
    private val malformedUdpDatagrams = AtomicLong(0)
    private val udpFragmentsDropped = AtomicLong(0)
    private val activeUdpAssociations = AtomicInteger(0)
    private val activeUdpAssociationsPeak = AtomicInteger(0)
    private val malformedProbeInjected = AtomicBoolean(false)
    private val malformedProbeEmptySent = AtomicLong(0)
    private val malformedProbeTruncatedSent = AtomicLong(0)
    private val malformedProbeInvalidHeaderSent = AtomicLong(0)
    private val sessionIoFailures = AtomicLong(0)
    private val executorShutdownTimeouts = AtomicLong(0)
    private val closeLock = Any()
    private val closed = AtomicBoolean(false)
    private val sessions = Collections.synchronizedSet(mutableSetOf<Closeable>())
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var sessionExecutor: ExecutorService? = null
    private var listenerResource: Closeable? = null
    private var lastCloseResult = VpnLocalSocksCloseResult(true, true)

    val port: Int
        get() = requireNotNull(serverSocket).localPort

    fun username(): String = credentials.usernameText()

    fun password(): String = credentials.passwordText()

    fun start() {
        check(!closed.get()) { "SOCKS5 server already closed" }
        check(running.compareAndSet(false, true)) { "SOCKS5 server already started" }
        val socket = ServerSocket()
        socket.reuseAddress = false
        socket.bind(InetSocketAddress(InetAddress.getByName(LoopbackIpv4), 0), maximumSessions)
        serverSocket = socket
        listenerResource = resources.acquire(VpnOwnedResourceKind.SocksListener)
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
                        val controlResource = resources.acquire(VpnOwnedResourceKind.SocksControl)
                        workers.execute {
                            try {
                                handleClient(client)
                            } catch (_: IOException) {
                                sessionIoFailures.incrementAndGet()
                            } finally {
                                sessions -= client
                                runCatching { client.close() }
                                controlResource.close()
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
        upstream.socket.soTimeout = RelayReadTimeoutMillis
        sessions += upstream
        try {
            tcpConnects.incrementAndGet()
            Socks5Protocol.writeReply(
                client.getOutputStream(),
                Socks5Protocol.Success,
                upstream.socket.localSocketAddress as InetSocketAddress,
            )
            client.soTimeout = 0
            val reverse = Thread({ relay(upstream.socket, client) }, "GloshSocksTcpReverse09A")
            reverse.start()
            relay(client, upstream.socket)
            runCatching { upstream.socket.shutdownOutput() }
            reverse.join(RelayJoinTimeoutMillis)
        } finally {
            sessions -= upstream
            upstream.close()
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
        val relayResource = resources.acquire(VpnOwnedResourceKind.SocksUdpRelay)
        val upstream = protectedSockets.openUdp()
        if (upstream == null) {
            relay.close()
            relayResource.close()
            Socks5Protocol.writeReply(control.getOutputStream(), Socks5Protocol.GeneralFailure)
            return
        }
        relay.soTimeout = UdpPollMillis
        upstream.socket.soTimeout = UdpResponseTimeoutMillis
        sessions += relay
        sessions += upstream
        udpAssociations.incrementAndGet()
        val activeAssociations = activeUdpAssociations.incrementAndGet()
        activeUdpAssociationsPeak.accumulateAndGet(activeAssociations, ::maxOf)
        val associationOpen = AtomicBoolean(true)
        val controlWatcher =
            Thread(
                {
                    runCatching { while (control.getInputStream().read() >= 0) Unit }
                    associationOpen.set(false)
                    relay.close()
                    upstream.close()
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
            associationLoop@ while (running.get() && associationOpen.get()) {
                val incoming = DatagramPacket(incomingBuffer, incomingBuffer.size)
                try {
                    relay.receive(incoming)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (error: SocketException) {
                    if (!running.get() || !associationOpen.get() || relay.isClosed) break@associationLoop
                    throw error
                }
                val source = incoming.socketAddress as? InetSocketAddress ?: continue
                if (!source.address.isLoopbackAddress || (clientEndpoint != null && source != clientEndpoint)) continue
                clientEndpoint = source
                val datagram =
                    when (val parsed = Socks5Protocol.classifyUdpDatagram(incoming.data, incoming.length)) {
                        Socks5UdpParseResult.Fragmented -> {
                            udpFragmentsDropped.incrementAndGet()
                            continue
                        }
                        Socks5UdpParseResult.Invalid -> {
                            malformedUdpDatagrams.incrementAndGet()
                            continue
                        }
                        is Socks5UdpParseResult.Parsed -> parsed.datagram
                    }
                if (!isAllowed(datagram.target)) {
                    malformedUdpDatagrams.incrementAndGet()
                    continue
                }
                try {
                    injectMalformedResponsesOnce(relay, clientEndpoint)
                    upstream.send(DatagramPacket(datagram.payload, datagram.payload.size, datagram.target))
                } catch (error: SocketException) {
                    if (!running.get() || !associationOpen.get() || relay.isClosed || upstream.socket.isClosed) {
                        break@associationLoop
                    }
                    throw error
                }
                udpDatagramsOut.incrementAndGet()
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                try {
                    upstream.socket.receive(response)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (error: SocketException) {
                    if (!running.get() || !associationOpen.get() || upstream.socket.isClosed) break@associationLoop
                    throw error
                }
                val responseSource = response.socketAddress as? InetSocketAddress ?: continue
                if (!isAllowed(responseSource)) continue
                udpDatagramsIn.incrementAndGet()
                val wrapped = Socks5Protocol.encodeUdpDatagram(responseSource, response.data, response.length)
                try {
                    relay.send(DatagramPacket(wrapped, wrapped.size, clientEndpoint))
                } catch (error: SocketException) {
                    if (!running.get() || !associationOpen.get() || relay.isClosed) break@associationLoop
                    throw error
                }
            }
        } finally {
            associationOpen.set(false)
            sessions -= relay
            sessions -= upstream
            relay.close()
            relayResource.close()
            upstream.close()
            controlWatcher.join(ControlWatcherJoinMillis)
            activeUdpAssociations.decrementAndGet()
        }
    }

    private fun injectMalformedResponsesOnce(
        relay: DatagramSocket,
        clientEndpoint: InetSocketAddress,
    ) {
        if (!malformedResponseProbeEnabled || !malformedProbeInjected.compareAndSet(false, true)) return
        relay.send(DatagramPacket(ByteArray(0), 0, clientEndpoint))
        malformedProbeEmptySent.incrementAndGet()
        relay.send(DatagramPacket(byteArrayOf(0, 0, 0), 3, clientEndpoint))
        malformedProbeTruncatedSent.incrementAndGet()
        val invalidHeader = byteArrayOf(1, 0, 0, 1, 127, 0, 0, 1, 0, 1)
        relay.send(DatagramPacket(invalidHeader, invalidHeader.size, clientEndpoint))
        malformedProbeInvalidHeaderSent.incrementAndGet()
    }

    private fun isAllowed(target: InetSocketAddress): Boolean = destinationAuthority.isAllowed(target)

    fun metrics(): VpnLocalSocksMetrics =
        VpnLocalSocksMetrics(
            acceptedConnections = acceptedConnections.get(),
            rejectedDestinations = rejectedDestinations.get(),
            tcpConnects = tcpConnects.get(),
            udpAssociations = udpAssociations.get(),
            udpDatagrams = udpDatagramsOut.get(),
            udpDatagramsOut = udpDatagramsOut.get(),
            udpDatagramsIn = udpDatagramsIn.get(),
            malformedUdpDatagrams = malformedUdpDatagrams.get(),
            udpFragmentsDropped = udpFragmentsDropped.get(),
            activeSessions = sessions.size,
            activeUdpAssociations = activeUdpAssociations.get(),
            activeUdpAssociationsPeak = activeUdpAssociationsPeak.get(),
            malformedProbeEmptySent = malformedProbeEmptySent.get(),
            malformedProbeTruncatedSent = malformedProbeTruncatedSent.get(),
            malformedProbeInvalidHeaderSent = malformedProbeInvalidHeaderSent.get(),
            sessionIoFailures = sessionIoFailures.get(),
            executorShutdownTimeouts = executorShutdownTimeouts.get(),
        )

    fun shutdown(): VpnLocalSocksCloseResult =
        synchronized(closeLock) {
            if (!closed.compareAndSet(false, true)) return@synchronized lastCloseResult
            running.set(false)
            runCatching { serverSocket?.close() }
            listenerResource?.close()
            listenerResource = null
            synchronized(sessions) { sessions.toList() }.forEach { runCatching { it.close() } }
            sessions.clear()
            val accept = acceptExecutor
            val workers = sessionExecutor
            accept?.shutdownNow()
            workers?.shutdownNow()
            val result =
                VpnLocalSocksCloseResult(
                    acceptExecutorTerminated = accept.awaitTerminationBounded(),
                    sessionExecutorTerminated = workers.awaitTerminationBounded(),
                )
            if (!result.clean) executorShutdownTimeouts.incrementAndGet()
            lastCloseResult = result
            credentials.close()
            serverSocket = null
            acceptExecutor = null
            sessionExecutor = null
            result
        }

    override fun close() {
        shutdown()
    }

    private fun ExecutorService?.awaitTerminationBounded(): Boolean {
        if (this == null) return true
        return try {
            awaitTermination(ExecutorShutdownMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private companion object {
        const val LoopbackIpv4 = "127.0.0.1"
        const val DefaultMaximumSessions = 8
        const val ControlTimeoutMillis = 5_000
        const val RelayReadTimeoutMillis = 30_000
        const val RelayJoinTimeoutMillis = 2_000L
        const val ControlWatcherJoinMillis = 1_000L

        // Active CONNECT workers may still be completing their bounded reverse-relay join after
        // both sockets are closed. Give that join a real margin before reporting quarantine.
        const val ExecutorShutdownMillis = RelayJoinTimeoutMillis + 3_000L
        const val UdpPollMillis = 250
        const val UdpResponseTimeoutMillis = 2_000
        const val TcpBufferSize = 16 * 1024
        const val MaximumUdpDatagramSize = 65_535
        val DefaultAllowedPorts = setOf(80, 443)
    }
}
