package com.contentfilter.core.domain.chrome

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Result of the non-authoritative parser-yield barrier used before the secret H19 HELLO. */
sealed interface ChromeMediaShieldParserBarrierResult {
    data object Ready : ChromeMediaShieldParserBarrierResult

    data object Rejected : ChromeMediaShieldParserBarrierResult

    data object Superseded : ChromeMediaShieldParserBarrierResult

    data object Unavailable : ChromeMediaShieldParserBarrierResult

    data object TimedOut : ChromeMediaShieldParserBarrierResult

    data object Interrupted : ChromeMediaShieldParserBarrierResult
}

interface ChromeMediaShieldParserBarrierCompletion {
    fun onTransportCancelled(callback: () -> Unit): ChromeMediaShieldActiveDocumentTransportCancellationRegistration

    fun ready(): Boolean

    fun reject(): Boolean

    fun supersede(): Boolean

    fun isPending(): Boolean
}

fun interface ChromeMediaShieldParserBarrierListener {
    fun onParserBarrierRequest(completion: ChromeMediaShieldParserBarrierCompletion)
}

data class ChromeMediaShieldParserBarrierSnapshot(
    val listenerRegistered: Boolean,
    val pendingRequests: Int,
    val requests: Long,
    val ready: Long,
    val rejected: Long,
    val superseded: Long,
    val unavailable: Long,
    val timedOut: Long,
    val interrupted: Long,
)

/**
 * One bounded parser-yield rendezvous. It conveys no token, claim, challenge, or authority.
 *
 * The external classic script keeps Chrome's parser before every original site token while the
 * browser UI thread remains free to publish its exact WebView Accessibility root. The later
 * secret HELLO still performs every authoritative current-document check.
 */
class ChromeMediaShieldParserBarrier(
    private val waitTimeoutMillis: Long = DefaultWaitTimeoutMillis,
) : AutoCloseable {
    private val lock = Any()
    private var listener: ChromeMediaShieldParserBarrierListener? = null
    private var registrationSequence = 0L
    private val pending = linkedSetOf<Pending>()
    private var requests = 0L
    private var ready = 0L
    private var rejected = 0L
    private var superseded = 0L
    private var unavailable = 0L
    private var timedOut = 0L
    private var interrupted = 0L
    private var closed = false

    init {
        require(waitTimeoutMillis in 1L..MaximumWaitTimeoutMillis)
    }

    fun register(newListener: ChromeMediaShieldParserBarrierListener): AutoCloseable {
        val orphaned: List<Pending>
        val sequence: Long
        synchronized(lock) {
            check(!closed) { "Parser barrier is closed" }
            registrationSequence += 1L
            sequence = registrationSequence
            listener = newListener
            orphaned = pending.toList()
            pending.clear()
        }
        orphaned.forEach { it.complete(ChromeMediaShieldParserBarrierResult.Unavailable) }
        return AutoCloseable { unregister(sequence) }
    }

    fun await(): ChromeMediaShieldParserBarrierResult {
        val currentListener: ChromeMediaShieldParserBarrierListener
        val request: Pending
        val supersededRequest: Pending?
        synchronized(lock) {
            requests += 1L
            currentListener = listener ?: return unavailableLocked()
            if (closed) return unavailableLocked()
            supersededRequest =
                if (pending.size >= MaximumPendingRequests) {
                    pending.first().also(pending::remove)
                } else {
                    null
                }
            request = Pending(this, waitTimeoutMillis)
            pending += request
        }
        supersededRequest?.complete(ChromeMediaShieldParserBarrierResult.Superseded)
        try {
            currentListener.onParserBarrierRequest(request)
        } catch (_: Throwable) {
            request.reject()
        }
        return request.await()
    }

    fun snapshot(): ChromeMediaShieldParserBarrierSnapshot =
        synchronized(lock) {
            ChromeMediaShieldParserBarrierSnapshot(
                listenerRegistered = listener != null,
                pendingRequests = pending.size,
                requests = requests,
                ready = ready,
                rejected = rejected,
                superseded = superseded,
                unavailable = unavailable,
                timedOut = timedOut,
                interrupted = interrupted,
            )
        }

    override fun close() {
        val orphaned: List<Pending>
        synchronized(lock) {
            if (closed) return
            closed = true
            listener = null
            orphaned = pending.toList()
            pending.clear()
        }
        orphaned.forEach { it.complete(ChromeMediaShieldParserBarrierResult.Unavailable) }
    }

    private fun unregister(sequence: Long) {
        val orphaned: List<Pending>
        synchronized(lock) {
            if (registrationSequence != sequence) return
            listener = null
            orphaned = pending.toList()
            pending.clear()
        }
        orphaned.forEach { it.complete(ChromeMediaShieldParserBarrierResult.Unavailable) }
    }

    private fun completeFromListener(
        request: Pending,
        result: ChromeMediaShieldParserBarrierResult,
    ): Boolean {
        val current = synchronized(lock) { !closed && request in pending && listener != null }
        // Never take Pending's monitor while holding the owner monitor. Timeout/cancellation takes
        // them in the opposite order when it publishes its terminal result.
        return current && request.complete(result)
    }

    private fun onTerminal(
        request: Pending,
        result: ChromeMediaShieldParserBarrierResult,
    ) {
        synchronized(lock) {
            pending.remove(request)
            when (result) {
                ChromeMediaShieldParserBarrierResult.Ready -> ready += 1L
                ChromeMediaShieldParserBarrierResult.Rejected -> rejected += 1L
                ChromeMediaShieldParserBarrierResult.Superseded -> superseded += 1L
                ChromeMediaShieldParserBarrierResult.Unavailable -> unavailable += 1L
                ChromeMediaShieldParserBarrierResult.TimedOut -> timedOut += 1L
                ChromeMediaShieldParserBarrierResult.Interrupted -> interrupted += 1L
            }
        }
    }

    private fun unavailableLocked(): ChromeMediaShieldParserBarrierResult {
        unavailable += 1L
        return ChromeMediaShieldParserBarrierResult.Unavailable
    }

    private class Pending(
        private val owner: ChromeMediaShieldParserBarrier,
        waitTimeoutMillis: Long,
    ) : ChromeMediaShieldParserBarrierCompletion {
        private val latch = CountDownLatch(1)
        private val timeoutMillis = waitTimeoutMillis
        private var result: ChromeMediaShieldParserBarrierResult? = null
        private var cancellationCallback: (() -> Unit)? = null

        override fun onTransportCancelled(
            callback: () -> Unit,
        ): ChromeMediaShieldActiveDocumentTransportCancellationRegistration {
            val registration: ChromeMediaShieldActiveDocumentTransportCancellationRegistration
            synchronized(this) {
                val terminal = result
                if (terminal == null) {
                    check(cancellationCallback == null) { "Parser barrier cleanup is already registered" }
                    cancellationCallback = callback
                    return ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered
                }
                registration =
                    if (terminal.isTransportCancellation()) {
                        ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled
                    } else {
                        ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCompleted
                    }
            }
            if (registration == ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled) {
                runCatching(callback)
            }
            return registration
        }

        override fun ready(): Boolean = owner.completeFromListener(this, ChromeMediaShieldParserBarrierResult.Ready)

        override fun reject(): Boolean = owner.completeFromListener(this, ChromeMediaShieldParserBarrierResult.Rejected)

        override fun supersede(): Boolean = complete(ChromeMediaShieldParserBarrierResult.Superseded)

        override fun isPending(): Boolean = synchronized(this) { result == null }

        fun complete(proposed: ChromeMediaShieldParserBarrierResult): Boolean {
            val callback: (() -> Unit)?
            synchronized(this) {
                if (result != null) return false
                result = proposed
                callback = cancellationCallback.takeIf { proposed.isTransportCancellation() }
                cancellationCallback = null
            }
            try {
                owner.onTerminal(this, proposed)
                callback?.let { runCatching(it) }
            } finally {
                latch.countDown()
            }
            return true
        }

        fun await(): ChromeMediaShieldParserBarrierResult {
            try {
                if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    if (!complete(ChromeMediaShieldParserBarrierResult.TimedOut)) latch.await()
                }
            } catch (_: InterruptedException) {
                complete(ChromeMediaShieldParserBarrierResult.Interrupted)
                Thread.currentThread().interrupt()
            }
            return synchronized(this) { checkNotNull(result) }
        }

        private fun ChromeMediaShieldParserBarrierResult.isTransportCancellation(): Boolean =
            this == ChromeMediaShieldParserBarrierResult.Unavailable ||
                this == ChromeMediaShieldParserBarrierResult.Superseded ||
                this == ChromeMediaShieldParserBarrierResult.TimedOut ||
                this == ChromeMediaShieldParserBarrierResult.Interrupted
    }

    private companion object {
        const val DefaultWaitTimeoutMillis = 5_000L
        const val MaximumWaitTimeoutMillis = 10_000L
        const val MaximumPendingRequests = 4
    }
}

object ChromeMediaShieldParserBarrierBridge {
    private val barrier = ChromeMediaShieldParserBarrier()

    fun register(listener: ChromeMediaShieldParserBarrierListener): AutoCloseable = barrier.register(listener)

    fun await(): ChromeMediaShieldParserBarrierResult = barrier.await()

    fun snapshot(): ChromeMediaShieldParserBarrierSnapshot = barrier.snapshot()
}
