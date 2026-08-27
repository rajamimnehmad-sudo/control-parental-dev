package com.contentfilter.feature.vpn.transport

import com.contentfilter.feature.vpn.service.VpnConnectionOwnerResult
import com.contentfilter.feature.vpn.service.VpnFlowTuple
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class VpnFlowOwnerCacheMetrics(
    val cacheHits: Long,
    val cacheMisses: Long,
    val lookupTimeouts: Long,
    val queueRejections: Long,
)

internal class VpnFlowOwnerCache(
    private val lookup: (VpnFlowTuple) -> VpnConnectionOwnerResult,
    private val nowMillis: () -> Long,
    private val capacity: Int = DefaultCapacity,
    private val ttlMillis: Long = DefaultTtlMillis,
    private val lookupWaitTimeoutMillis: Long = DefaultLookupWaitTimeoutMillis,
    private val lookupExecutor: Executor = DefaultLookupExecutor,
) {
    init {
        require(capacity > 0)
        require(ttlMillis > 0)
        require(lookupWaitTimeoutMillis > 0)
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
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val lookupTimeouts = AtomicLong(0)
    private val queueRejections = AtomicLong(0)

    fun resolve(
        flow: VpnFlowTuple,
        generation: Long,
    ): VpnConnectionOwnerResult {
        val now = nowMillis()
        synchronized(cache) {
            cache[flow]
                ?.takeIf { it.generation == generation && now < it.expiresAtMillis }
                ?.let {
                    cacheHits.incrementAndGet()
                    return it.result
                }
            cache.remove(flow)
        }
        cacheMisses.incrementAndGet()
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
        if (leader) {
            try {
                lookupExecutor.execute { performLookup(key, flow, generation, future) }
            } catch (_: RejectedExecutionException) {
                queueRejections.incrementAndGet()
                synchronized(inFlight) { inFlight.remove(key, future) }
                future.complete(VpnConnectionOwnerResult.Unknown)
            }
        }
        return try {
            future.get(lookupWaitTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            lookupTimeouts.incrementAndGet()
            VpnConnectionOwnerResult.Unknown
        } catch (_: CancellationException) {
            VpnConnectionOwnerResult.Unknown
        }
    }

    private fun performLookup(
        key: Pair<Long, VpnFlowTuple>,
        flow: VpnFlowTuple,
        generation: Long,
        future: CompletableFuture<VpnConnectionOwnerResult>,
    ) {
        try {
            val result = lookup(flow)
            if (!future.isCancelled) {
                synchronized(cache) {
                    cache[flow] = Entry(generation, nowMillis() + ttlMillis, result)
                }
                future.complete(result)
            }
        } catch (error: Throwable) {
            future.completeExceptionally(error)
        } finally {
            synchronized(inFlight) { inFlight.remove(key, future) }
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

    fun metrics(): VpnFlowOwnerCacheMetrics =
        VpnFlowOwnerCacheMetrics(
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            lookupTimeouts = lookupTimeouts.get(),
            queueRejections = queueRejections.get(),
        )

    private companion object {
        const val DefaultCapacity = 512
        const val DefaultTtlMillis = 30_000L
        const val DefaultLookupWaitTimeoutMillis = 750L
        const val LookupThreadCount = 2
        const val LookupQueueCapacity = 64
        const val LoadFactor = 0.75f
        val lookupThreadCounter = AtomicInteger(0)
        val DefaultLookupExecutor: Executor =
            ThreadPoolExecutor(
                LookupThreadCount,
                LookupThreadCount,
                30L,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(LookupQueueCapacity),
                { task ->
                    Thread(task, "GloshVpnOwnerLookup-${lookupThreadCounter.incrementAndGet()}").apply {
                        isDaemon = true
                    }
                },
                ThreadPoolExecutor.AbortPolicy(),
            ).apply { allowCoreThreadTimeOut(true) }
    }
}
