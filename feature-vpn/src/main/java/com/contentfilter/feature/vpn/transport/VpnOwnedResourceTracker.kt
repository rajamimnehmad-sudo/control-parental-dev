package com.contentfilter.feature.vpn.transport

import java.io.Closeable
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal enum class VpnOwnedResourceKind {
    PacketBridgeFd,
    SocksListener,
    SocksControl,
    SocksUdpRelay,
    ProtectedTcp,
    ProtectedUdp,
}

internal data class VpnOwnedResourceSnapshot(
    val ownedFdResources: Int,
    val ownedFdResourcesPeak: Int,
    val activeProtectedUdpSockets: Int,
    val activeProtectedUdpSocketsPeak: Int,
)

internal class VpnOwnedResourceTracker {
    private val counts = EnumMap<VpnOwnedResourceKind, AtomicInteger>(VpnOwnedResourceKind::class.java)
    private val total = AtomicInteger(0)
    private val totalPeak = AtomicInteger(0)
    private val protectedUdpPeak = AtomicInteger(0)

    init {
        VpnOwnedResourceKind.entries.forEach { kind -> counts[kind] = AtomicInteger(0) }
    }

    fun acquire(
        kind: VpnOwnedResourceKind,
        count: Int = 1,
    ): Closeable {
        require(count > 0)
        val kindCount = requireNotNull(counts[kind]).addAndGet(count)
        val currentTotal = total.addAndGet(count)
        totalPeak.accumulateAndGet(currentTotal, ::maxOf)
        if (kind == VpnOwnedResourceKind.ProtectedUdp) {
            protectedUdpPeak.accumulateAndGet(kindCount, ::maxOf)
        }
        return ResourceLease {
            check(requireNotNull(counts[kind]).addAndGet(-count) >= 0) { "Negative resource count for $kind" }
            check(total.addAndGet(-count) >= 0) { "Negative owned resource total" }
        }
    }

    fun snapshot(): VpnOwnedResourceSnapshot =
        VpnOwnedResourceSnapshot(
            ownedFdResources = total.get(),
            ownedFdResourcesPeak = totalPeak.get(),
            activeProtectedUdpSockets = requireNotNull(counts[VpnOwnedResourceKind.ProtectedUdp]).get(),
            activeProtectedUdpSocketsPeak = protectedUdpPeak.get(),
        )

    private class ResourceLease(
        private val release: () -> Unit,
    ) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }
}

internal object VpnTransportResourceDiagnostics {
    @Volatile
    private var latest = VpnOwnedResourceSnapshot(0, 0, 0, 0)

    fun publish(snapshot: VpnOwnedResourceSnapshot) {
        latest = snapshot
    }

    fun snapshot(): VpnOwnedResourceSnapshot = latest
}
