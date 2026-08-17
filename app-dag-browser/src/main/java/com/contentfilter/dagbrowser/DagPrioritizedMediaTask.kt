package com.contentfilter.dagbrowser

import java.util.AbstractQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal enum class DagMediaAnalysisPriority(
    internal val rank: Int,
) {
    Visible(0),
    Nearby(1),
    Background(2),
    ;

    companion object {
        fun fromWire(value: String): DagMediaAnalysisPriority =
            when (value) {
                "visible" -> Visible
                "nearby" -> Nearby
                else -> Background
            }
    }
}

internal data class DagMediaDocumentIdentity(
    val tabId: Int,
    val documentToken: String,
)

internal class DagPrioritizedMediaTask(
    private val priority: DagMediaAnalysisPriority,
    private val sequence: Long,
    internal val documentIdentity: DagMediaDocumentIdentity? = null,
    internal val videoLabKey: DagVideoLabKey? = null,
    private val onDiscard: () -> Unit = {},
    private val action: () -> Unit,
) : Runnable,
    Comparable<DagPrioritizedMediaTask> {
    private val claimed = AtomicBoolean(false)

    override fun run() {
        if (claimed.compareAndSet(false, true)) action()
    }

    internal fun discard() {
        if (claimed.compareAndSet(false, true)) onDiscard()
    }

    override fun compareTo(other: DagPrioritizedMediaTask): Int {
        val priorityOrder = priority.rank.compareTo(other.priority.rank)
        return if (priorityOrder != 0) priorityOrder else sequence.compareTo(other.sequence)
    }
}

/**
 * Keeps the media executor priority-aware without allowing captured Base64 payloads to accumulate
 * without a hard bound.
 *
 * The executor only submits [DagPrioritizedMediaTask] instances. Rejecting any other runnable
 * keeps that invariant explicit instead of silently degrading ordering.
 */
internal class DagBoundedMediaTaskQueue(
    private val capacity: Int,
) : AbstractQueue<Runnable>(),
    BlockingQueue<Runnable> {
    private val availableSlots: Semaphore
    private val delegate: PriorityBlockingQueue<Runnable>

    init {
        require(capacity > 0)
        availableSlots = Semaphore(capacity, true)
        delegate =
            PriorityBlockingQueue(capacity) { left, right ->
                requireMediaTask(left).compareTo(requireMediaTask(right))
            }
    }

    override val size: Int
        get() = delegate.size

    override fun offer(element: Runnable): Boolean {
        requireMediaTask(element)
        if (!availableSlots.tryAcquire()) return false
        return addAfterPermit(element)
    }

    override fun put(element: Runnable) {
        requireMediaTask(element)
        availableSlots.acquire()
        addAfterPermit(element)
    }

    override fun offer(
        element: Runnable,
        timeout: Long,
        unit: TimeUnit,
    ): Boolean {
        requireMediaTask(element)
        if (!availableSlots.tryAcquire(timeout, unit)) return false
        return addAfterPermit(element)
    }

    override fun poll(): Runnable? = releaseSlotAfter(delegate.poll())

    override fun poll(
        timeout: Long,
        unit: TimeUnit,
    ): Runnable? = releaseSlotAfter(delegate.poll(timeout, unit))

    override fun take(): Runnable = delegate.take().also { availableSlots.release() }

    override fun peek(): Runnable? = delegate.peek()

    override fun remainingCapacity(): Int = availableSlots.availablePermits()

    override fun drainTo(target: MutableCollection<in Runnable>): Int = drainTo(target, Int.MAX_VALUE)

    override fun drainTo(
        target: MutableCollection<in Runnable>,
        maxElements: Int,
    ): Int {
        require(target !== this)
        if (maxElements <= 0) return 0
        var drained = 0
        while (drained < maxElements) {
            val element = delegate.poll() ?: break
            try {
                target.add(element)
                drained += 1
            } finally {
                availableSlots.release()
            }
        }
        return drained
    }

    override fun remove(element: Runnable?): Boolean {
        if (element == null) return false
        val removed = delegate.remove(element)
        if (removed) availableSlots.release()
        return removed
    }

    override fun clear() {
        val discarded = ArrayList<Runnable>(size)
        drainTo(discarded)
    }

    fun discardMatching(predicate: (DagPrioritizedMediaTask) -> Boolean): Int {
        var discarded = 0
        val snapshot = delegate.toTypedArray()
        for (runnable in snapshot) {
            val task = runnable as? DagPrioritizedMediaTask ?: continue
            if (!predicate(task) || !delegate.remove(task)) continue
            availableSlots.release()
            task.discard()
            discarded += 1
        }
        return discarded
    }

    override fun iterator(): MutableIterator<Runnable> {
        val snapshotIterator = delegate.iterator()
        return object : MutableIterator<Runnable> {
            private var last: Runnable? = null

            override fun hasNext(): Boolean = snapshotIterator.hasNext()

            override fun next(): Runnable = snapshotIterator.next().also { last = it }

            override fun remove() {
                val item = last ?: throw IllegalStateException("next() was not called")
                this@DagBoundedMediaTaskQueue.remove(item)
                last = null
            }
        }
    }

    private fun addAfterPermit(element: Runnable): Boolean {
        var added = false
        return try {
            added = delegate.offer(element)
            added
        } finally {
            if (!added) availableSlots.release()
        }
    }

    private fun releaseSlotAfter(element: Runnable?): Runnable? {
        if (element != null) availableSlots.release()
        return element
    }

    private fun requireMediaTask(runnable: Runnable): DagPrioritizedMediaTask =
        requireNotNull(runnable as? DagPrioritizedMediaTask) {
            "Only DagPrioritizedMediaTask can enter the media queue"
        }
}

internal fun interface DagMediaWorkGuard {
    fun canContinue(): Boolean
}

internal object AlwaysCurrentDagMediaWork : DagMediaWorkGuard {
    override fun canContinue(): Boolean = true
}

/** Tracks the exact top-level document currently owned by each WebExtension tab. */
internal class DagMediaDocumentRegistry {
    private data class Record(
        val identity: DagMediaDocumentIdentity,
        val topLevelDocumentToken: String?,
        val privateDocument: Boolean?,
    )

    private val currentDocuments = ConcurrentHashMap<Int, Record>()

    fun markCurrent(
        tabId: Int,
        documentToken: String,
        topLevelDocumentToken: String? = null,
    ) {
        require(tabId >= 0)
        require(documentToken.isNotEmpty())
        val identity = DagMediaDocumentIdentity(tabId, documentToken)
        currentDocuments.compute(tabId) { _, existing ->
            if (existing?.identity == identity) {
                existing.copy(
                    topLevelDocumentToken = topLevelDocumentToken ?: existing.topLevelDocumentToken,
                )
            } else {
                Record(
                    identity = identity,
                    topLevelDocumentToken = topLevelDocumentToken,
                    privateDocument = null,
                )
            }
        }
    }

    fun bindPrivacy(
        topLevelDocumentToken: String,
        privateDocument: Boolean,
    ) {
        require(topLevelDocumentToken.isNotEmpty())
        currentDocuments.forEach { (tabId, record) ->
            if (record.topLevelDocumentToken != topLevelDocumentToken) return@forEach
            currentDocuments.computeIfPresent(tabId) { _, current ->
                if (
                    current.identity == record.identity &&
                    current.topLevelDocumentToken == topLevelDocumentToken
                ) {
                    current.copy(privateDocument = privateDocument)
                } else {
                    current
                }
            }
        }
    }

    fun allowsDiagnostics(identity: DagMediaDocumentIdentity): Boolean =
        currentDocuments[identity.tabId]?.let { record ->
            record.identity == identity && record.privateDocument == false
        } == true

    fun retire(
        tabId: Int,
        documentToken: String,
    ): Boolean {
        val current = currentDocuments[tabId] ?: return false
        return current.identity.documentToken == documentToken && currentDocuments.remove(tabId, current)
    }

    fun isCurrent(
        tabId: Int,
        documentToken: String,
    ): Boolean = currentDocuments[tabId]?.identity?.documentToken == documentToken

    fun clear() = currentDocuments.clear()
}

/** Converts the extension wall-clock timestamp into a bounded monotonic budget. */
internal object DagMediaAnalysisDeadline {
    fun remainingMillis(
        sentAtEpochMillis: Long,
        nowEpochMillis: Long,
        lifetimeMillis: Long,
        allowedFutureSkewMillis: Long,
    ): Long? {
        if (sentAtEpochMillis <= 0L || lifetimeMillis <= 0L || allowedFutureSkewMillis < 0L) {
            return null
        }
        val ageMillis = nowEpochMillis - sentAtEpochMillis
        if (ageMillis < -allowedFutureSkewMillis) return null
        return (lifetimeMillis - ageMillis.coerceAtLeast(0L)).takeIf { it > 0L }
    }
}

/**
 * A task remains valid only inside the native deadline and the analyzer lifecycle generation in
 * which it was accepted, and while its exact browser document remains current.
 */
internal class DagMediaAnalysisLease(
    private val generation: Long,
    private val deadlineElapsedRealtime: Long,
    private val currentGeneration: () -> Long,
    private val elapsedRealtime: () -> Long,
    private val acceptingWork: () -> Boolean,
    private val documentCurrent: () -> Boolean = { true },
) : DagMediaWorkGuard {
    override fun canContinue(): Boolean =
        acceptingWork() &&
            currentGeneration() == generation &&
            documentCurrent() &&
            elapsedRealtime() < deadlineElapsedRealtime &&
            !Thread.currentThread().isInterrupted
}
