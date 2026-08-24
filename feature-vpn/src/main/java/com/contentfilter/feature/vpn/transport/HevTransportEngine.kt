package com.contentfilter.feature.vpn.transport

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class HevTransportStopResult(
    val joined: Boolean,
    val nativeResult: Int?,
    val state: HevTransportLifecycleState,
    val cleanupComplete: Boolean,
)

internal enum class HevTransportLifecycleState {
    Stopped,
    Running,
    StopRequested,
    Quarantined,
}

internal data class HevTransportLifecycleSnapshot(
    val state: HevTransportLifecycleState,
    val joinTimeouts: Long,
    val cleanupCount: Long,
)

internal class HevTransportEngine(
    private val nativeApi: HevNativeApi = HevNativeBridge,
    private val bridgeFactory: VpnPacketBridgeFactory = AndroidVpnPacketBridge,
    private val resources: VpnOwnedResourceTracker = VpnOwnedResourceTracker(),
    private val nativeJoinTimeoutMillis: Long = NativeJoinTimeoutMillis,
    private val responseJoinTimeoutMillis: Long = ResponseJoinTimeoutMillis,
) : Closeable {
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val packetWrites = AtomicLong(0)
    private val packetWriteFailures = AtomicLong(0)
    private val packetsReturned = AtomicLong(0)
    private val joinTimeouts = AtomicLong(0)
    private val cleanupCount = AtomicLong(0)
    private var lifecycleState = HevTransportLifecycleState.Stopped
    private var bridge: VpnPacketBridge? = null
    private var nativeThread: Thread? = null
    private var responseThread: Thread? = null
    private var nativeResult: Int? = null
    private var configBytes: ByteArray? = null
    private var bridgeResources: Closeable? = null

    fun start(
        socksPort: Int,
        username: String,
        password: String,
        onPacketFromHev: (ByteArray) -> Unit,
    ): PacketSocketType {
        val started = CountDownLatch(1)
        val openedBridge =
            try {
                synchronized(lifecycleLock) {
                    check(lifecycleState == HevTransportLifecycleState.Stopped && nativeThread == null) {
                        "HEV transport cannot start state=$lifecycleState"
                    }
                    check(running.compareAndSet(false, true)) { "HEV transport already running" }
                    lifecycleState = HevTransportLifecycleState.Running
                    nativeResult = null
                    val openedBridge = bridgeFactory.open()
                    bridge = openedBridge
                    bridgeResources = resources.acquire(VpnOwnedResourceKind.PacketBridgeFd, PacketBridgeOwnedFdCount)
                    val config = config(socksPort, username, password).toByteArray(Charsets.UTF_8)
                    configBytes = config
                    nativeThread =
                        Thread(
                            {
                                started.countDown()
                                var result: Int? = null
                                try {
                                    result = nativeApi.run(config, openedBridge.engineFd)
                                } finally {
                                    onNativeRunReturned(Thread.currentThread(), result)
                                }
                            },
                            "GloshHevNative09A",
                        ).also { it.start() }
                    responseThread =
                        Thread(
                            {
                                val buffer = ByteArray(MaximumPacketSize)
                                while (running.get()) {
                                    val length = openedBridge.readPacket(buffer)
                                    if (length <= 0) break
                                    packetsReturned.incrementAndGet()
                                    onPacketFromHev(buffer.copyOf(length))
                                }
                            },
                            "GloshHevResponse09A",
                        ).also { it.start() }
                    openedBridge.type
                }
            } catch (error: Throwable) {
                stop()
                throw error
            }
        try {
            check(started.await(StartWaitMillis, TimeUnit.MILLISECONDS)) { "HEV thread did not start" }
            Thread.sleep(InitializationObservationMillis)
            check(isRunning()) { "HEV exited during initialization result=$nativeResult" }
            return openedBridge
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun writePacket(packet: ByteArray): Boolean {
        if (!running.get()) return false
        val written = bridge?.writePacket(packet) == true
        if (written) packetWrites.incrementAndGet() else packetWriteFailures.incrementAndGet()
        return written
    }

    fun stats(): HevNativeStats = nativeApi.stats()

    fun isRunning(): Boolean =
        synchronized(lifecycleLock) {
            lifecycleState == HevTransportLifecycleState.Running && running.get() && nativeThread?.isAlive == true
        }

    fun stop(): HevTransportStopResult {
        val thread =
            synchronized(lifecycleLock) {
                val currentThread = nativeThread
                if (currentThread == null) {
                    running.set(false)
                    cleanupLocked()
                    lifecycleState = HevTransportLifecycleState.Stopped
                    return HevTransportStopResult(
                        joined = true,
                        nativeResult = nativeResult,
                        state = lifecycleState,
                        cleanupComplete = lifecycleState == HevTransportLifecycleState.Stopped,
                    )
                }
                running.set(false)
                if (lifecycleState != HevTransportLifecycleState.Quarantined) {
                    lifecycleState = HevTransportLifecycleState.StopRequested
                }
                currentThread
            }
        runCatching { nativeApi.quit() }
        val joined =
            try {
                thread.join(nativeJoinTimeoutMillis)
                !thread.isAlive
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        return synchronized(lifecycleLock) {
            if (nativeThread === thread) {
                if (joined) {
                    cleanupLocked()
                    lifecycleState = HevTransportLifecycleState.Stopped
                } else {
                    lifecycleState = HevTransportLifecycleState.Quarantined
                    joinTimeouts.incrementAndGet()
                }
            }
            HevTransportStopResult(
                joined = joined,
                nativeResult = nativeResult,
                state = lifecycleState,
                cleanupComplete = lifecycleState == HevTransportLifecycleState.Stopped && nativeThread == null,
            )
        }
    }

    fun lifecycleSnapshot(): HevTransportLifecycleSnapshot =
        synchronized(lifecycleLock) {
            HevTransportLifecycleSnapshot(
                state = lifecycleState,
                joinTimeouts = joinTimeouts.get(),
                cleanupCount = cleanupCount.get(),
            )
        }

    private fun onNativeRunReturned(
        thread: Thread,
        result: Int?,
    ) {
        running.set(false)
        synchronized(lifecycleLock) {
            if (nativeThread !== thread) return
            nativeResult = result
            cleanupLocked()
            lifecycleState = HevTransportLifecycleState.Stopped
        }
    }

    private fun cleanupLocked() {
        if (bridge == null && bridgeResources == null && configBytes == null && nativeThread == null) return
        runCatching { bridge?.close() }
        bridgeResources?.close()
        responseThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                runCatching { thread.join(responseJoinTimeoutMillis) }
            }
        }
        configBytes?.fill(0)
        configBytes = null
        bridge = null
        bridgeResources = null
        nativeThread = null
        responseThread = null
        cleanupCount.incrementAndGet()
    }

    fun packetWriteCount(): Long = packetWrites.get()

    fun packetWriteFailureCount(): Long = packetWriteFailures.get()

    fun packetReturnCount(): Long = packetsReturned.get()

    override fun close() {
        stop()
    }

    private fun config(
        socksPort: Int,
        username: String,
        password: String,
    ): String =
        """
        tunnel:
          name: tun0
          mtu: 1500
          multi-queue: false
          ipv4: 10.8.0.2
          ipv6: 'fd00:1:fd00:1::2'
          icmp: 'off'
        socks5:
          port: $socksPort
          address: 127.0.0.1
          udp: 'udp'
          username: '$username'
          password: '$password'
        misc:
          max-session-count: 64
          connect-timeout: 5000
          tcp-read-write-timeout: 30000
          udp-read-write-timeout: 10000
          log-file: null
          log-level: warn
        """.trimIndent()

    private companion object {
        const val MaximumPacketSize = 32 * 1024
        const val StartWaitMillis = 1_000L
        const val InitializationObservationMillis = 20L
        const val NativeJoinTimeoutMillis = 5_000L
        const val ResponseJoinTimeoutMillis = 1_000L
        const val PacketBridgeOwnedFdCount = 3
    }
}
