package com.contentfilter.feature.vpn.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.feature.vpn.service.ChromePhotosUdpFixtureGate
import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResolver
import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnPacketParseResult
import com.contentfilter.feature.vpn.service.VpnPacketParser
import com.contentfilter.feature.vpn.service.VpnTransportProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class VpnTransportGateMetrics(
    val generation: Long,
    val queuePeak: Int,
    val queueDrops: Long,
    val forwardedPackets: Long,
    val returnedPackets: Long,
    val chromeTcpDrops: Long,
    val chromeUdpDrops: Long,
    val unknownOwnerDrops: Long,
    val recursionPackets: Long,
    val packetSocketType: PacketSocketType,
    val hev: HevNativeStats,
    val hevLifecycle: HevTransportLifecycleSnapshot,
    val socks: VpnLocalSocksMetrics,
    val protectedSockets: VpnProtectedSocketMetrics,
    val resources: VpnOwnedResourceSnapshot,
)

/** Bounded DEV feasibility datapath for the existing controlled /32-/128 routes. */
internal class VpnTransportGate09A private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    initialGeneration: Long,
    private val ownerResolver: VpnConnectionOwnerResolver,
    private val ownerCache: VpnFlowOwnerCache,
    private val policy: VpnTransportPolicy,
    private val protectedSockets: VpnProtectedSocketFactory,
    private val socks: VpnLocalSocks5Server,
    private val engine: HevTransportEngine,
    private val resources: VpnOwnedResourceTracker,
    private val udpStressTarget: InetSocketAddress?,
    private val packetSocketType: PacketSocketType,
    private val writeToTun: (ByteArray) -> Boolean,
) : Closeable {
    private data class Packet(
        val bytes: ByteArray,
        val parsed: VpnPacketParseResult.Parsed,
    )

    private val queue = Channel<Packet>(QueueCapacity)
    private val closed = AtomicBoolean(false)
    private val authorityGeneration = AtomicLong(initialGeneration)
    private val jobs = mutableListOf<Job>()
    private val queueDepth = AtomicInteger(0)
    private val queuePeak = AtomicInteger(0)
    private val queueDrops = AtomicLong(0)
    private val forwardedPackets = AtomicLong(0)
    private val chromeTcpDrops = AtomicLong(0)
    private val chromeUdpDrops = AtomicLong(0)
    private val unknownOwnerDrops = AtomicLong(0)
    private val recursionPackets = AtomicLong(0)
    private val loggedFlows =
        Collections.synchronizedMap(
            object : LinkedHashMap<com.contentfilter.feature.vpn.service.VpnFlowTuple, Unit>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<com.contentfilter.feature.vpn.service.VpnFlowTuple, Unit>?,
                ): Boolean = size > 64
            },
        )
    private val stressLock = Any()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val observedUnderlyingNetworks = Collections.synchronizedSet(mutableSetOf<Long>())
    private val observedFirstUnderlyingNetwork = AtomicBoolean(false)
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!observedUnderlyingNetworks.add(network.networkHandle)) return
                if (observedFirstUnderlyingNetwork.getAndSet(true)) {
                    invalidateNetworkGeneration("underlying_available")
                }
            }

            override fun onLost(network: Network) {
                if (observedUnderlyingNetworks.remove(network.networkHandle)) {
                    invalidateNetworkGeneration("underlying_lost")
                }
            }
        }

    init {
        repeat(WorkerCount) {
            jobs += scope.launch { for (packet in queue) handle(packet) }
        }
        runCatching {
            requireNotNull(connectivityManager).registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                networkCallback,
            )
        }.onFailure {
            queue.close()
            jobs.forEach { job -> job.cancel() }
            engine.close()
            socks.close()
        }.getOrThrow()
    }

    fun submitIfTransport(
        packet: ByteArray,
        length: Int,
    ): Boolean {
        val parsed = VpnPacketParser.parse(packet, length) as? VpnPacketParseResult.Parsed ?: return false
        if (parsed.flow.protocol == VpnTransportProtocol.Udp && parsed.flow.remoteAddress.port == DnsPort) {
            return false
        }
        if (parsed.tcpFlags?.let { it.syn && !it.ack } == true) ownerCache.invalidate(parsed.flow)
        val depth = queueDepth.incrementAndGet()
        queuePeak.accumulateAndGet(depth, ::maxOf)
        val accepted = queue.trySend(Packet(packet.copyOf(parsed.packetLength), parsed)).isSuccess
        if (!accepted) {
            queueDepth.decrementAndGet()
            queueDrops.incrementAndGet()
        }
        return true
    }

    private fun handle(packet: Packet) {
        queueDepth.decrementAndGet()
        val owner =
            runCatching { ownerCache.resolve(packet.parsed.flow, authorityGeneration.get()) }
                .getOrDefault(VpnConnectionOwnerResult.Unknown)
        val action = policy.decide(packet.parsed.flow, owner)
        when (action) {
            VpnTransportAction.ForwardToHev -> {
                if (owner is VpnConnectionOwnerResult.Resolved && context.packageName in owner.packages) {
                    recursionPackets.incrementAndGet()
                } else if (engine.writePacket(packet.bytes)) {
                    forwardedPackets.incrementAndGet()
                } else {
                    queueDrops.incrementAndGet()
                }
            }
            VpnTransportAction.DropChromeDirectHttps -> {
                if (packet.parsed.flow.protocol == VpnTransportProtocol.Tcp) {
                    chromeTcpDrops.incrementAndGet()
                } else {
                    chromeUdpDrops.incrementAndGet()
                }
            }
            VpnTransportAction.DropUnknownOwner -> unknownOwnerDrops.incrementAndGet()
            VpnTransportAction.ExistingDnsPath,
            VpnTransportAction.DropUnapprovedDestination,
            -> Unit
        }
        logOnce(packet.parsed, owner, action)
        if (packet.parsed.tcpFlags?.let { it.fin || it.rst } == true) ownerCache.invalidate(packet.parsed.flow)
    }

    private fun logOnce(
        parsed: VpnPacketParseResult.Parsed,
        owner: VpnConnectionOwnerResult,
        action: VpnTransportAction,
    ) {
        if (loggedFlows.put(parsed.flow, Unit) != null) return
        val ownerLabel =
            when (owner) {
                is VpnConnectionOwnerResult.Resolved ->
                    "uid=${owner.uid} packages=${owner.packages.joinToString(",").ifBlank { "unmapped" }}"
                VpnConnectionOwnerResult.PermissionDenied -> "owner=permission_denied"
                VpnConnectionOwnerResult.Unknown -> "owner=unknown"
            }
        Log.i(
            LogTag,
            "generation=${authorityGeneration.get()} protocol=${parsed.flow.protocol.name.lowercase()} " +
                "remote=${parsed.flow.remoteAddress.address.hostAddress}:${parsed.flow.remoteAddress.port} " +
                "$ownerLabel action=${action.name.lowercase()}",
        )
    }

    fun metrics(): VpnTransportGateMetrics =
        VpnTransportGateMetrics(
            generation = authorityGeneration.get(),
            queuePeak = queuePeak.get(),
            queueDrops = queueDrops.get(),
            forwardedPackets = forwardedPackets.get(),
            returnedPackets = engine.packetReturnCount(),
            chromeTcpDrops = chromeTcpDrops.get(),
            chromeUdpDrops = chromeUdpDrops.get(),
            unknownOwnerDrops = unknownOwnerDrops.get(),
            recursionPackets = recursionPackets.get(),
            packetSocketType = packetSocketType,
            hev = engine.stats(),
            hevLifecycle = engine.lifecycleSnapshot(),
            socks = socks.metrics(),
            protectedSockets = protectedSockets.metrics(),
            resources = resources.snapshot(),
        ).also { metrics -> VpnTransportResourceDiagnostics.publish(metrics.resources) }

    fun runNativeStress(cycles: Int): Int =
        synchronized(stressLock) {
            require(cycles in 1..MaximumStressCycles)
            val target = udpStressTarget ?: throw IllegalStateException("No UDP fixture configured for HEV stress")
            engine.stop().also { check(it.joined) { "HEV did not join before stress" } }
            var completed = 0
            repeat(cycles) {
                engine.start(
                    socksPort = socks.port,
                    username = socks.username(),
                    password = socks.password(),
                    onPacketFromHev = { packet -> writeToTun(packet) },
                )
                val roundTrips = it % MaximumStressRoundTripsPerCycle + 1
                repeat(roundTrips) { sequence ->
                    val before = socks.metrics()
                    check(engine.writePacket(udpStressPacket(target, it, sequence))) {
                        "HEV stress write failed cycle=${it + 1} sequence=$sequence"
                    }
                    check(waitForUdpRoundTrip(before, target)) {
                        "HEV UDP roundtrip timeout cycle=${it + 1} sequence=$sequence"
                    }
                }
                val stopped = engine.stop()
                check(stopped.joined) { "HEV stress join failed cycle=${it + 1}" }
                check(waitForUdpAssociationsClosed()) {
                    "SOCKS UDP association did not close cycle=${it + 1}"
                }
                completed++
            }
            engine.start(
                socksPort = socks.port,
                username = socks.username(),
                password = socks.password(),
                onPacketFromHev = { packet -> writeToTun(packet) },
            )
            completed
        }

    private fun waitForUdpRoundTrip(
        before: VpnLocalSocksMetrics,
        target: InetSocketAddress,
    ): Boolean {
        val deadline = System.nanoTime() + StressRoundTripTimeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            val current = socks.metrics()
            if (
                current.udpDatagramsOut > before.udpDatagramsOut &&
                current.udpDatagramsIn > before.udpDatagramsIn
            ) {
                return true
            }
            Thread.sleep(StressPollMillis)
        }
        Log.w(LogTag, "stress=udp_timeout target=${target.address.hostAddress}:${target.port}")
        return false
    }

    private fun waitForUdpAssociationsClosed(): Boolean {
        val deadline = System.nanoTime() + StressAssociationCloseTimeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (socks.metrics().activeUdpAssociations == 0) return true
            Thread.sleep(StressPollMillis)
        }
        return false
    }

    private fun udpStressPacket(
        target: InetSocketAddress,
        cycle: Int,
        sequence: Int,
    ): ByteArray =
        ByteArray(28 + StressPayloadSize).apply {
            this[0] = 0x45
            this[2] = 0
            this[3] = size.toByte()
            this[8] = 64
            this[9] = 17
            byteArrayOf(10, 8, 0, 2).copyInto(this, 12)
            target.address.address.copyInto(this, 16)
            this[20] = 0x9C.toByte()
            this[21] = 0x40
            this[22] = (target.port ushr 8).toByte()
            this[23] = target.port.toByte()
            val udpLength = size - 20
            this[24] = (udpLength ushr 8).toByte()
            this[25] = udpLength.toByte()
            this[28] = 0x47
            this[29] = 0x4C
            this[30] = cycle.toByte()
            this[31] = sequence.toByte()
            val checksum = ipv4Checksum(this, 20)
            this[10] = (checksum ushr 8).toByte()
            this[11] = checksum.toByte()
        }

    private fun ipv4Checksum(
        packet: ByteArray,
        headerLength: Int,
    ): Int {
        var sum = 0L
        var offset = 0
        while (offset < headerLength) {
            sum += ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
            offset += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun invalidateNetworkGeneration(reason: String) {
        if (closed.get()) return
        val next = generationCounter.incrementAndGet()
        authorityGeneration.set(next)
        ownerCache.clear()
        ownerResolver.clear()
        loggedFlows.clear()
        Log.i(LogTag, "generation=$next owner_authority=invalidated reason=$reason")
    }

    override fun close() {
        val firstClose = closed.compareAndSet(false, true)
        if (firstClose) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            queue.close()
            jobs.forEach { it.cancel() }
        }
        val engineStop = engine.stop()
        val socksStop = socks.shutdown()
        if (firstClose) {
            ownerCache.clear()
            ownerResolver.clear()
            loggedFlows.clear()
        }
        VpnTransportResourceDiagnostics.publish(resources.snapshot())
        check(engineStop.joined && engineStop.cleanupComplete) {
            "HEV lifecycle quarantined state=${engineStop.state}"
        }
        if (!socksStop.clean) {
            // All listeners/sessions/upstream sockets are already closed and Chrome is fail-closed.
            // Keep the bounded timeout observable without crashing the VPN service's main thread.
            Log.e(
                LogTag,
                "socks_shutdown=quarantined accept=${socksStop.acceptExecutorTerminated} " +
                    "sessions=${socksStop.sessionExecutorTerminated}",
            )
        }
    }

    companion object {
        private const val QueueCapacity = 128
        private const val WorkerCount = 2
        private const val DnsPort = 53
        private const val MaximumStressCycles = 200
        private const val StressPayloadSize = 8
        private const val MaximumStressRoundTripsPerCycle = 5
        private const val StressPollMillis = 10L
        private const val StressRoundTripTimeoutMillis = 2_000L
        private const val StressAssociationCloseTimeoutMillis = 2_000L
        private const val LogTag = "VpnTransport09A"
        private val generationCounter = AtomicLong(0)

        fun start(
            vpnService: VpnService,
            scope: CoroutineScope,
            allowedAddresses: Set<String>,
            allowedPorts: Set<Int>,
            udpFixtureGate: ChromePhotosUdpFixtureGate?,
            writeToTun: (ByteArray) -> Boolean,
        ): VpnTransportGate09A {
            require(vpnService.packageName.endsWith(".dev"))
            require(allowedAddresses.isNotEmpty())
            val generation = generationCounter.incrementAndGet()
            val resolver = VpnConnectionOwnerResolver.create(vpnService)
            val ownerCache = VpnFlowOwnerCache(resolver::resolve, android.os.SystemClock::elapsedRealtime)
            val resources = VpnOwnedResourceTracker()
            val protectedSockets =
                VpnProtectedSocketFactory(
                    protectTcp = { socket: Socket -> vpnService.protect(socket) },
                    protectUdp = { socket: DatagramSocket -> vpnService.protect(socket) },
                    resources = resources,
                )
            val socks =
                VpnLocalSocks5Server(
                    protectedSockets = protectedSockets,
                    allowedAddresses = allowedAddresses,
                    allowedPorts = allowedPorts,
                    resources = resources,
                    malformedResponseProbeEnabled = udpFixtureGate?.malformedProbeEnabled == true,
                )
            socks.start()
            val engine = HevTransportEngine(resources = resources)
            val socketType =
                try {
                    engine.start(
                        socksPort = socks.port,
                        username = socks.username(),
                        password = socks.password(),
                        onPacketFromHev = { packet ->
                            if (writeToTun(packet)) {
                                // Counter belongs to the gate created below; native stats remains
                                // the authority during the small construction window.
                            }
                        },
                    )
                } catch (error: Throwable) {
                    engine.close()
                    socks.close()
                    throw error
                }
            val policy =
                VpnTransportPolicy(
                    chromePackage = ChromePhotosDataPlaneLabContract.ChromePackage,
                    allowedDestinationAddresses = allowedAddresses.mapTo(hashSetOf()) { it.substringBefore('%') },
                )
            return VpnTransportGate09A(
                context = vpnService,
                scope = scope,
                initialGeneration = generation,
                ownerResolver = resolver,
                ownerCache = ownerCache,
                policy = policy,
                protectedSockets = protectedSockets,
                socks = socks,
                engine = engine,
                resources = resources,
                udpStressTarget =
                    udpFixtureGate?.let { gate ->
                        InetSocketAddress(InetAddress.getByName(gate.address) as Inet4Address, gate.port)
                    },
                packetSocketType = socketType,
                writeToTun = writeToTun,
            ).also {
                // Rebind the response counter through the HEV authoritative counters at status time.
                Log.i(LogTag, "generation=$generation engine=started bridge=${socketType.name.lowercase()}")
            }
        }
    }
}
