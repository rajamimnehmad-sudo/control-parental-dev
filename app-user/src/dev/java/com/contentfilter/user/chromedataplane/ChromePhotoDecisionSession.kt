package com.contentfilter.user.chromedataplane

import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class ChromePhotoDecisionSessionMetrics(
    val requests: Long = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val dedupeHits: Long = 0,
    val engineCalls: Long = 0,
    val inferencePeak: Int = 0,
    val inFlightPeak: Int = 0,
    val queuePeak: Int = 0,
    val queueRejects: Long = 0,
    val timeouts: Long = 0,
    val safe: Long = 0,
    val block: Long = 0,
    val unknown: Long = 0,
    val cacheEntries: Int = 0,
    val cacheEvictions: Long = 0,
    val inFlightEntries: Int = 0,
    val preprocessP50Ms: Double = 0.0,
    val preprocessP95Ms: Double = 0.0,
    val preprocessP99Ms: Double = 0.0,
    val preparedImageCount1: Long = 0,
    val preparedImageCount4: Long = 0,
    val preparedImageCount5: Long = 0,
    val preparedImageCountOther: Long = 0,
    val decisionBasisCounts: Map<String, Long> = emptyMap(),
    val engineCallsPerRequest: Double = 0.0,
    val inferenceP50Ms: Double = 0.0,
    val inferenceP95Ms: Double = 0.0,
    val inferenceP99Ms: Double = 0.0,
    val decisionP50Ms: Double = 0.0,
    val decisionP95Ms: Double = 0.0,
    val decisionP99Ms: Double = 0.0,
    val cacheHitP50Ms: Double = 0.0,
    val cacheHitP95Ms: Double = 0.0,
)

internal interface ChromePhotoDecisionSession : AutoCloseable {
    fun decide(
        contentHash: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult

    fun cacheSize(): Int

    fun clear()

    fun metrics(): ChromePhotoDecisionSessionMetrics = ChromePhotoDecisionSessionMetrics(cacheEntries = cacheSize())

    override fun close() = clear()
}

internal class ChromePhotosBoundedDecisionSession(
    private val engine: ChromePhotoDecisionEngine,
    private val maximumCacheEntries: Int = DefaultMaximumCacheEntries,
    maximumQueueEntries: Int = DefaultMaximumQueueEntries,
    maximumConcurrentInferences: Int = DefaultMaximumConcurrentInferences,
    private val timeoutMillis: Long = DefaultTimeoutMillis,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onSystemicFailure: (String) -> Unit = {},
) : ChromePhotoDecisionSession {
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong()
    private val systemicFailureNotified = AtomicBoolean(false)
    private val inFlight = ConcurrentHashMap<DecisionKey, DecisionTask>()
    private val cacheLock = Any()
    private val cacheEvictions = AtomicLong()
    private val cache =
        object : LinkedHashMap<DecisionKey, ChromePhotoDecisionResult>(maximumCacheEntries, LoadFactor, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<DecisionKey, ChromePhotoDecisionResult>?,
            ): Boolean {
                return (size > maximumCacheEntries).also { evicted ->
                    if (evicted) cacheEvictions.incrementAndGet()
                }
            }
        }
    private val executor =
        ThreadPoolExecutor(
            maximumConcurrentInferences,
            maximumConcurrentInferences,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(maximumQueueEntries),
            { runnable -> Thread(runnable, "chrome-photos-gloshia").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val requests = AtomicLong()
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val dedupeHits = AtomicLong()
    private val engineCalls = AtomicLong()
    private val activeInferences = AtomicInteger()
    private val inferencePeak = AtomicInteger()
    private val inFlightPeak = AtomicInteger()
    private val queuePeak = AtomicInteger()
    private val queueRejects = AtomicLong()
    private val timeouts = AtomicLong()
    private val safe = AtomicLong()
    private val block = AtomicLong()
    private val unknown = AtomicLong()
    private val inferenceSamples = BoundedTimingSamples()
    private val decisionSamples = BoundedTimingSamples()
    private val cacheHitSamples = BoundedTimingSamples()
    private val preprocessSamples = BoundedTimingSamples()
    private val preparedImageCount1 = AtomicLong()
    private val preparedImageCount4 = AtomicLong()
    private val preparedImageCount5 = AtomicLong()
    private val preparedImageCountOther = AtomicLong()
    private val decisionBasisCounts = ConcurrentHashMap<String, AtomicLong>()

    init {
        require(maximumCacheEntries > 0)
        require(maximumQueueEntries > 0)
        require(maximumConcurrentInferences > 0)
        require(timeoutMillis > 0)
    }

    override fun decide(
        contentHash: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult {
        val started = nanoTime()
        requests.incrementAndGet()
        if (closed.get()) return record(unknown(ClosedReason), started)
        val decisionGeneration = generation.get()
        val canonicalMimeType = mimeType.normalizedImageMimeType()
        val key =
            DecisionKey(
                identity = engine.identity.cacheKey,
                generation = decisionGeneration,
                canonicalMimeType = canonicalMimeType,
                contentHash = contentHash,
            )
        synchronized(cacheLock) { cache[key] }?.let { cached ->
            if (generation.get() != decisionGeneration) {
                return record(unknown(StaleGenerationReason, ChromePhotoDecisionSource.Unavailable), started)
            }
            cacheHits.incrementAndGet()
            val result =
                cached.copy(
                    source = ChromePhotoDecisionSource.Cache,
                    timings = ChromePhotoDecisionTimings(totalLocalMs = (nanoTime() - started).toMillis()),
                )
            cacheHitSamples.add(result.timings.totalLocalMs)
            return record(result, started, replaceTotal = false)
        }
        cacheMisses.incrementAndGet()

        val candidate = DecisionTask(key, imageBytes, canonicalMimeType, decisionGeneration)
        val existing = inFlight.putIfAbsent(key, candidate)
        val task = existing ?: candidate
        if (existing == null) {
            updatePeak(inFlightPeak, inFlight.size)
            try {
                executor.execute(task)
                updatePeak(queuePeak, executor.queue.size)
            } catch (_: RejectedExecutionException) {
                candidate.discard()
                candidate.cancel(false)
                inFlight.remove(key, candidate)
                queueRejects.incrementAndGet()
                return record(unknown(QueueFullReason, ChromePhotoDecisionSource.QueueFull), started)
            }
        } else {
            dedupeHits.incrementAndGet()
        }

        val result =
            try {
                task.get(timeoutMillis, TimeUnit.MILLISECONDS).let { completed ->
                    if (existing == null || completed.source != ChromePhotoDecisionSource.Engine) {
                        completed
                    } else {
                        completed.copy(source = ChromePhotoDecisionSource.InFlight)
                    }
                }
            } catch (_: TimeoutException) {
                timeouts.incrementAndGet()
                task.discard()
                task.cancel(true)
                inFlight.remove(key, task)
                unknown(TimeoutReason, ChromePhotoDecisionSource.Timeout)
            } catch (_: CancellationException) {
                unknown(CancelledReason, ChromePhotoDecisionSource.Unavailable)
            } catch (_: ExecutionException) {
                unknown(EngineExceptionReason, ChromePhotoDecisionSource.Error)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                unknown(InterruptedReason, ChromePhotoDecisionSource.Unavailable)
            }
        return if (generation.get() == decisionGeneration) {
            record(result, started)
        } else {
            record(unknown(StaleGenerationReason, ChromePhotoDecisionSource.Unavailable), started)
        }
    }

    override fun cacheSize(): Int = synchronized(cacheLock) { cache.size }

    override fun clear() {
        generation.incrementAndGet()
        inFlight.values.forEach { task ->
            task.discard()
            task.cancel(true)
        }
        inFlight.clear()
        synchronized(cacheLock) { cache.clear() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        clear()
        executor.shutdownNow()
        engine.close()
    }

    override fun metrics(): ChromePhotoDecisionSessionMetrics {
        val inference = inferenceSamples.snapshot()
        val decision = decisionSamples.snapshot()
        val cacheHit = cacheHitSamples.snapshot()
        val preprocess = preprocessSamples.snapshot()
        return ChromePhotoDecisionSessionMetrics(
            requests = requests.get(),
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            dedupeHits = dedupeHits.get(),
            engineCalls = engineCalls.get(),
            inferencePeak = activeInferencePeak(),
            inFlightPeak = inFlightPeak.get(),
            queuePeak = queuePeak.get(),
            queueRejects = queueRejects.get(),
            timeouts = timeouts.get(),
            safe = safe.get(),
            block = block.get(),
            unknown = unknown.get(),
            cacheEntries = cacheSize(),
            cacheEvictions = cacheEvictions.get(),
            inFlightEntries = inFlight.size,
            preprocessP50Ms = preprocess.percentile(50),
            preprocessP95Ms = preprocess.percentile(95),
            preprocessP99Ms = preprocess.percentile(99),
            preparedImageCount1 = preparedImageCount1.get(),
            preparedImageCount4 = preparedImageCount4.get(),
            preparedImageCount5 = preparedImageCount5.get(),
            preparedImageCountOther = preparedImageCountOther.get(),
            decisionBasisCounts = decisionBasisCounts.mapValues { it.value.get() }.toSortedMap(),
            engineCallsPerRequest = engineCalls.get().toRate(requests.get()),
            inferenceP50Ms = inference.percentile(50),
            inferenceP95Ms = inference.percentile(95),
            inferenceP99Ms = inference.percentile(99),
            decisionP50Ms = decision.percentile(50),
            decisionP95Ms = decision.percentile(95),
            decisionP99Ms = decision.percentile(99),
            cacheHitP50Ms = cacheHit.percentile(50),
            cacheHitP95Ms = cacheHit.percentile(95),
        )
    }

    private fun runEngine(
        bytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult {
        engineCalls.incrementAndGet()
        val active = activeInferences.incrementAndGet()
        updatePeak(inferencePeak, active)
        return try {
            engine.decide(bytes, mimeType).also { result ->
                inferenceSamples.add(result.timings.inferenceMs)
                preprocessSamples.add(result.timings.decodeAndPreprocessMs)
                when (result.preparedImageCount) {
                    1 -> preparedImageCount1.incrementAndGet()
                    4 -> preparedImageCount4.incrementAndGet()
                    5 -> preparedImageCount5.incrementAndGet()
                    else -> preparedImageCountOther.incrementAndGet()
                }
                val basis = result.basis.takeIf(KnownDecisionBases::contains) ?: OtherBasis
                decisionBasisCounts.computeIfAbsent(basis) { AtomicLong() }.incrementAndGet()
                if (!engine.isHealthy() && systemicFailureNotified.compareAndSet(false, true)) {
                    onSystemicFailure(result.reason)
                }
            }
        } finally {
            activeInferences.decrementAndGet()
        }
    }

    private fun record(
        result: ChromePhotoDecisionResult,
        started: Long,
        replaceTotal: Boolean = true,
    ): ChromePhotoDecisionResult {
        when (result.decision) {
            ChromePhotoDecision.Safe -> safe.incrementAndGet()
            ChromePhotoDecision.Block -> block.incrementAndGet()
            ChromePhotoDecision.Unknown -> unknown.incrementAndGet()
        }
        val recorded =
            if (replaceTotal) {
                result.copy(
                    timings = result.timings.copy(totalLocalMs = (nanoTime() - started).toMillis()),
                )
            } else {
                result
            }
        decisionSamples.add(recorded.timings.totalLocalMs)
        return recorded
    }

    private fun unknown(
        reason: String,
        source: ChromePhotoDecisionSource = ChromePhotoDecisionSource.Unavailable,
    ) = ChromePhotoDecisionResult(ChromePhotoDecision.Unknown, reason, source)

    private fun cacheIfCurrent(
        key: DecisionKey,
        result: ChromePhotoDecisionResult,
        taskGeneration: Long,
        discarded: Boolean,
    ) {
        if (
            discarded || closed.get() || generation.get() != taskGeneration ||
            result.source != ChromePhotoDecisionSource.Engine
        ) {
            return
        }
        synchronized(cacheLock) { cache[key] = result }
    }

    private fun activeInferencePeak(): Int = inferencePeak.get()

    private inner class DecisionTask(
        val key: DecisionKey,
        bytes: ByteArray,
        mimeType: String,
        private val taskGeneration: Long,
    ) : Runnable {
        private val discarded = AtomicBoolean(false)
        private val future = FutureTask(DecisionWork(key, bytes, mimeType, taskGeneration, discarded))

        fun discard() {
            discarded.set(true)
        }

        override fun run() {
            try {
                future.run()
            } finally {
                inFlight.remove(key, this)
            }
        }

        fun get(
            timeout: Long,
            unit: TimeUnit,
        ): ChromePhotoDecisionResult = future.get(timeout, unit)

        fun cancel(mayInterruptIfRunning: Boolean): Boolean = future.cancel(mayInterruptIfRunning)
    }

    private inner class DecisionWork(
        private val key: DecisionKey,
        private val bytes: ByteArray,
        private val mimeType: String,
        private val taskGeneration: Long,
        private val discarded: AtomicBoolean,
    ) : Callable<ChromePhotoDecisionResult> {
        override fun call(): ChromePhotoDecisionResult =
            runEngine(bytes, mimeType).also { result ->
                cacheIfCurrent(key, result, taskGeneration, discarded.get())
            }
    }

    private data class DecisionKey(
        val identity: String,
        val generation: Long,
        val canonicalMimeType: String,
        val contentHash: String,
    )

    private companion object {
        const val DefaultMaximumCacheEntries = 64
        const val DefaultMaximumQueueEntries = 2
        const val DefaultMaximumConcurrentInferences = 1
        const val DefaultTimeoutMillis = 5_000L
        const val LoadFactor = 0.75f
        const val ClosedReason = "decision_session_closed"
        const val QueueFullReason = "decision_queue_full"
        const val TimeoutReason = "decision_timeout"
        const val CancelledReason = "decision_cancelled"
        const val EngineExceptionReason = "decision_engine_exception"
        const val InterruptedReason = "decision_interrupted"
        const val StaleGenerationReason = "decision_generation_stale"
        const val OtherBasis = "Other"
        val KnownDecisionBases =
            setOf(
                "None",
                "FullThreshold",
                "FullStrong",
                "UncertainRegional",
                "RegionalStrong",
                "RegionalConsensus",
            )
    }
}

private class BoundedTimingSamples(
    private val maximumEntries: Int = 512,
) {
    private val values = ArrayDeque<Double>()

    @Synchronized
    fun add(value: Double) {
        if (!value.isFinite() || value < 0.0) return
        if (values.size == maximumEntries) values.removeFirst()
        values.addLast(value)
    }

    @Synchronized
    fun snapshot(): List<Double> = values.toList()
}

private fun List<Double>.percentile(percentile: Int): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val index = ((percentile / 100.0) * (sorted.size - 1)).toInt().coerceIn(sorted.indices)
    return sorted[index]
}

private fun updatePeak(
    peak: AtomicInteger,
    candidate: Int,
) {
    while (true) {
        val current = peak.get()
        if (candidate <= current || peak.compareAndSet(current, candidate)) return
    }
}

private fun Long.toMillis(): Double = this / 1_000_000.0

private fun Long.toRate(denominator: Long): Double = if (denominator == 0L) 0.0 else toDouble() / denominator
