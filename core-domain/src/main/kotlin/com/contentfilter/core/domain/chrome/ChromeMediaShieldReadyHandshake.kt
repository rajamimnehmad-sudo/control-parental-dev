package com.contentfilter.core.domain.chrome

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Terminal result of the foreground document READY handshake.
 *
 * [Accepted] can only be produced by the registered presentation listener after it has committed
 * the opaque surface. Every other result is fail-closed and must not be mapped to a browser ACK.
 */
sealed interface ChromeMediaShieldReadyHandshakeResult {
    data object Accepted : ChromeMediaShieldReadyHandshakeResult

    data object Rejected : ChromeMediaShieldReadyHandshakeResult

    data object Unavailable : ChromeMediaShieldReadyHandshakeResult

    data object TimedOut : ChromeMediaShieldReadyHandshakeResult

    data object Interrupted : ChromeMediaShieldReadyHandshakeResult
}

/** One-shot completion owned by the native presentation listener. */
interface ChromeMediaShieldReadyHandshakeCompletion {
    /**
     * Completes the request successfully only after the opaque presentation barrier is committed.
     * Returns false when another terminal transition already won.
     */
    fun acceptAfterOpaqueCommit(): Boolean

    /** Completes the request fail-closed. Returns false after any previous terminal transition. */
    fun reject(): Boolean
}

fun interface ChromeMediaShieldReadyHandshakeListener {
    fun onReadyClaim(
        claim: ChromeMediaShieldReadyClaim,
        completion: ChromeMediaShieldReadyHandshakeCompletion,
    )
}

data class ChromeMediaShieldReadyHandshakeSnapshot(
    val listenerRegistered: Boolean,
    val pendingRequests: Int,
    val closed: Boolean,
)

/**
 * Bounded, process-local bridge between a validated READY claim and native presentation.
 *
 * The caller blocks only on [CountDownLatch], with a fixed upper bound and thread-interruption
 * cancellation. There is no polling, retry, or temporal inference of authority. Requests keep only
 * the already-digested [ChromeMediaShieldReadyClaim]; raw READY tokens never cross this boundary.
 */
class ChromeMediaShieldReadyHandshake(
    private val waitTimeoutMillis: Long = DefaultWaitTimeoutMillis,
    private val maximumPendingRequests: Int = DefaultMaximumPendingRequests,
) : AutoCloseable {
    private val lock = Any()
    private var listenerRegistration: ListenerRegistration? = null
    private var nextRegistrationId = 0L
    private var closed = false
    private val pendingRequests = linkedSetOf<PendingRequest>()

    init {
        require(waitTimeoutMillis in 1L..MaximumWaitTimeoutMillis)
        require(maximumPendingRequests in 1..MaximumAllowedPendingRequests)
    }

    /** Registers the single native owner. A second live owner is a programming error. */
    fun register(listener: ChromeMediaShieldReadyHandshakeListener): AutoCloseable {
        synchronized(lock) {
            check(!closed) { "READY handshake is closed" }
            check(listenerRegistration == null) { "READY handshake listener already registered" }
            val registration = ListenerRegistration(++nextRegistrationId, listener)
            listenerRegistration = registration
            return RegistrationHandle(registration.id)
        }
    }

    /**
     * Waits for the native owner to commit the opaque barrier and explicitly accept [claim].
     *
     * A timeout or interruption wins atomically and makes every later callback inert.
     */
    fun awaitOpaqueCommit(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyHandshakeResult {
        val registration: ListenerRegistration
        val pending: PendingRequest
        synchronized(lock) {
            registration = listenerRegistration ?: return ChromeMediaShieldReadyHandshakeResult.Unavailable
            if (closed || pendingRequests.size >= maximumPendingRequests) {
                return ChromeMediaShieldReadyHandshakeResult.Unavailable
            }
            pending =
                PendingRequest(
                    owner = this,
                    registrationId = registration.id,
                    waitTimeoutMillis = waitTimeoutMillis,
                )
            pendingRequests += pending
        }

        try {
            registration.listener.onReadyClaim(claim, pending)
        } catch (_: Throwable) {
            pending.complete(ChromeMediaShieldReadyHandshakeResult.Rejected)
        }
        return pending.await()
    }

    fun snapshot(): ChromeMediaShieldReadyHandshakeSnapshot =
        synchronized(lock) {
            ChromeMediaShieldReadyHandshakeSnapshot(
                listenerRegistered = listenerRegistration != null,
                pendingRequests = pendingRequests.size,
                closed = closed,
            )
        }

    override fun close() {
        val orphaned: List<PendingRequest>
        synchronized(lock) {
            if (closed) return
            closed = true
            listenerRegistration = null
            orphaned = pendingRequests.toList()
        }
        orphaned.forEach { request ->
            request.complete(ChromeMediaShieldReadyHandshakeResult.Unavailable)
        }
    }

    private fun unregister(registrationId: Long) {
        val orphaned: List<PendingRequest>
        synchronized(lock) {
            if (listenerRegistration?.id != registrationId) return
            listenerRegistration = null
            orphaned = pendingRequests.filter { request -> request.registrationId == registrationId }
        }
        orphaned.forEach { request ->
            request.complete(ChromeMediaShieldReadyHandshakeResult.Unavailable)
        }
    }

    private fun onTerminal(request: PendingRequest) {
        synchronized(lock) {
            pendingRequests.remove(request)
        }
    }

    private fun completeFromListener(
        request: PendingRequest,
        terminalResult: ChromeMediaShieldReadyHandshakeResult,
    ): Boolean =
        synchronized(lock) {
            if (
                closed ||
                listenerRegistration?.id != request.registrationId ||
                request !in pendingRequests
            ) {
                return@synchronized false
            }
            request.completeFromCurrentListener(terminalResult)
        }

    private data class ListenerRegistration(
        val id: Long,
        val listener: ChromeMediaShieldReadyHandshakeListener,
    )

    private inner class RegistrationHandle(
        private val registrationId: Long,
    ) : AutoCloseable {
        private var handleClosed = false

        override fun close() {
            synchronized(this) {
                if (handleClosed) return
                handleClosed = true
            }
            unregister(registrationId)
        }
    }

    private class PendingRequest(
        private val owner: ChromeMediaShieldReadyHandshake,
        val registrationId: Long,
        waitTimeoutMillis: Long,
    ) : ChromeMediaShieldReadyHandshakeCompletion {
        private val latch = CountDownLatch(1)
        private val timeoutMillis = waitTimeoutMillis
        private val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(waitTimeoutMillis)
        private val startedAtNanos = System.nanoTime()
        private var result: ChromeMediaShieldReadyHandshakeResult? = null

        override fun acceptAfterOpaqueCommit(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldReadyHandshakeResult.Accepted,
            )

        override fun reject(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldReadyHandshakeResult.Rejected,
            )

        fun completeFromCurrentListener(terminalResult: ChromeMediaShieldReadyHandshakeResult): Boolean {
            val acceptedBeforeDeadline: Boolean
            synchronized(this) {
                if (result != null) return false
                acceptedBeforeDeadline = System.nanoTime() - startedAtNanos < timeoutNanos
                result =
                    if (acceptedBeforeDeadline) {
                        terminalResult
                    } else {
                        ChromeMediaShieldReadyHandshakeResult.TimedOut
                    }
                latch.countDown()
            }
            owner.onTerminal(this)
            return acceptedBeforeDeadline
        }

        fun complete(terminalResult: ChromeMediaShieldReadyHandshakeResult): Boolean {
            synchronized(this) {
                if (result != null) return false
                result = terminalResult
                latch.countDown()
            }
            owner.onTerminal(this)
            return true
        }

        fun await(): ChromeMediaShieldReadyHandshakeResult {
            try {
                if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    complete(ChromeMediaShieldReadyHandshakeResult.TimedOut)
                }
            } catch (_: InterruptedException) {
                complete(ChromeMediaShieldReadyHandshakeResult.Interrupted)
                Thread.currentThread().interrupt()
            }
            return synchronized(this) { checkNotNull(result) }
        }
    }

    private companion object {
        const val DefaultWaitTimeoutMillis = 1_500L
        const val MaximumWaitTimeoutMillis = 10_000L
        const val DefaultMaximumPendingRequests = 32
        const val MaximumAllowedPendingRequests = 128
    }
}

/** Process-local rendezvous shared by the DEV proxy and Accessibility presentation owner. */
object ChromeMediaShieldReadyHandshakeBridge {
    private val handshake = ChromeMediaShieldReadyHandshake()

    fun register(listener: ChromeMediaShieldReadyHandshakeListener): AutoCloseable = handshake.register(listener)

    fun awaitCurrentPresentation(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyHandshakeResult =
        handshake.awaitOpaqueCommit(claim)

    fun snapshot(): ChromeMediaShieldReadyHandshakeSnapshot = handshake.snapshot()
}
