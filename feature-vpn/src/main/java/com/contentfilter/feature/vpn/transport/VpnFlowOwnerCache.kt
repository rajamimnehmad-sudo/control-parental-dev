package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture

internal class VpnFlowOwnerCache(
    private val lookup: (VpnFlowTuple) -> VpnConnectionOwnerResult,
    private val nowMillis: () -> Long,
    private val capacity: Int = DefaultCapacity,
    private val ttlMillis: Long = DefaultTtlMillis,
) {
    init {
        require(capacity > 0)
        require(ttlMillis > 0)
    }

    private data class Entry(
        val generation: Long,
        val expiresAtMillis: Long,
        val result: VpnConnectionOwnerResult,
    )

    private val cache =
        object : LinkedHashMap<VpnFlowTuple, Entry>(capacity, LoadFactor, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VpnFlowTuple, Entry>?): Boolean =
                size > capacity
        }
    private val inFlight = mutableMapOf<Pair<Long, VpnFlowTuple>, CompletableFuture<VpnConnectionOwnerResult>>()

    fun resolve(
        flow: VpnFlowTuple,
        generation: Long,
    ): VpnConnectionOwnerResult {
        val now = nowMillis()
        synchronized(cache) {
            cache[flow]
                ?.takeIf { it.generation == generation && now < it.expiresAtMillis }
                ?.let { return it.result }
            cache.remove(flow)
        }
        val key = generation to flow
        val future: CompletableFuture<VpnConnectionOwnerResult>
        val leader: Boolean
        synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing != null) {
                future = existing
                leader = false
            } else {
                future = CompletableFuture()
                inFlight[key] = future
                leader = true
            }
        }
        if (!leader) return future.get()
        return try {
            val result = lookup(flow)
            synchronized(cache) {
                cache[flow] = Entry(generation, nowMillis() + ttlMillis, result)
            }
            future.complete(result)
            result
        } catch (error: Throwable) {
            future.completeExceptionally(error)
            throw error
        } finally {
            synchronized(inFlight) { inFlight.remove(key) }
        }
    }

    fun invalidate(flow: VpnFlowTuple) {
        synchronized(cache) { cache.remove(flow) }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
        synchronized(inFlight) {
            inFlight.values.forEach { it.cancel(false) }
            inFlight.clear()
        }
    }

    internal fun size(): Int = synchronized(cache) { cache.size }

    private companion object {
        const val DefaultCapacity = 512
        const val DefaultTtlMillis = 30_000L
        const val LoadFactor = 0.75f
    }
}
