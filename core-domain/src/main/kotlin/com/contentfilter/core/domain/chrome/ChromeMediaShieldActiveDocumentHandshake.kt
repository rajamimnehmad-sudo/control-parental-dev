package com.contentfilter.core.domain.chrome

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Network phase proved by the protected, parser-blocking H19 bootstrap. */
enum class ChromeMediaShieldActiveDocumentPhase {
    Hello,
    Prove,
    Present,
    Revoke,
}

/**
 * One ephemeral challenge. Its value is intentionally excluded from [toString].
 *
 * The endpoint may serialize [encoded] only into the capability-authenticated response body. It
 * must never put it in a URL, DOM node, metric, or log message.
 */
class ChromeMediaShieldActiveDocumentChallenge private constructor(
    val encoded: String,
) {
    init {
        require(encoded.length in MinimumCharacters..MaximumCharacters)
        require(encoded.all { character -> character.isLetterOrDigit() || character == '-' || character == '_' })
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ChromeMediaShieldActiveDocumentChallenge && encoded == other.encoded)

    override fun hashCode(): Int = encoded.hashCode()

    override fun toString(): String = "ChromeMediaShieldActiveDocumentChallenge(redacted)"

    companion object {
        /** Parses the URL-safe, unpadded capability value supplied by the trusted endpoint. */
        fun fromEncoded(encoded: String): ChromeMediaShieldActiveDocumentChallenge =
            ChromeMediaShieldActiveDocumentChallenge(encoded)

        const val MinimumCharacters = 22
        const val MaximumCharacters = 128
    }
}

/** A request carries only a registry-validated claim and, after HELLO, the opaque challenge. */
sealed interface ChromeMediaShieldActiveDocumentRequest {
    val claim: ChromeMediaShieldReadyClaim
    val phase: ChromeMediaShieldActiveDocumentPhase

    data class Hello(
        override val claim: ChromeMediaShieldReadyClaim,
    ) : ChromeMediaShieldActiveDocumentRequest {
        override val phase = ChromeMediaShieldActiveDocumentPhase.Hello
    }

    data class Prove(
        override val claim: ChromeMediaShieldReadyClaim,
        val challenge: ChromeMediaShieldActiveDocumentChallenge,
    ) : ChromeMediaShieldActiveDocumentRequest {
        override val phase = ChromeMediaShieldActiveDocumentPhase.Prove
    }

    data class Present(
        override val claim: ChromeMediaShieldReadyClaim,
        val challenge: ChromeMediaShieldActiveDocumentChallenge,
    ) : ChromeMediaShieldActiveDocumentRequest {
        override val phase = ChromeMediaShieldActiveDocumentPhase.Present
    }

    data class Revoke(
        override val claim: ChromeMediaShieldReadyClaim,
        val challenge: ChromeMediaShieldActiveDocumentChallenge,
    ) : ChromeMediaShieldActiveDocumentRequest {
        override val phase = ChromeMediaShieldActiveDocumentPhase.Revoke
    }
}

/** Terminal result returned to the DEV endpoint. No result contains a raw document token. */
sealed interface ChromeMediaShieldActiveDocumentHandshakeResult {
    data class ChallengeIssued(
        val challenge: ChromeMediaShieldActiveDocumentChallenge,
    ) : ChromeMediaShieldActiveDocumentHandshakeResult {
        override fun toString(): String = "ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(redacted)"
    }

    data object ProofAccepted : ChromeMediaShieldActiveDocumentHandshakeResult

    data object PresentationAccepted : ChromeMediaShieldActiveDocumentHandshakeResult

    data object Revoked : ChromeMediaShieldActiveDocumentHandshakeResult

    data object Rejected : ChromeMediaShieldActiveDocumentHandshakeResult

    data object Unavailable : ChromeMediaShieldActiveDocumentHandshakeResult

    data object TimedOut : ChromeMediaShieldActiveDocumentHandshakeResult

    data object Interrupted : ChromeMediaShieldActiveDocumentHandshakeResult
}

/** Exact outcome of installing the one transport-cancellation cleanup callback. */
enum class ChromeMediaShieldActiveDocumentTransportCancellationRegistration {
    Registered,
    AlreadyCancelled,
    AlreadyCompleted,
}

/** One-shot completion owned by the current native listener generation. */
interface ChromeMediaShieldActiveDocumentHandshakeCompletion {
    /**
     * Registers one fail-closed callback for timeout, interruption, listener supersession, or
     * bridge close. The callback never runs for a normal accepted/rejected phase result.
     */
    fun onTransportCancelled(callback: () -> Unit): ChromeMediaShieldActiveDocumentTransportCancellationRegistration

    fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge): Boolean

    fun acceptProof(): Boolean

    fun acceptPresentation(): Boolean

    fun acceptRevocation(): Boolean

    fun reject(): Boolean
}

fun interface ChromeMediaShieldActiveDocumentHandshakeListener {
    fun onActiveDocumentRequest(
        request: ChromeMediaShieldActiveDocumentRequest,
        completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    )
}

/** Bounded aggregate telemetry. It never retains claims, challenges, or request history. */
data class ChromeMediaShieldActiveDocumentHandshakeSnapshot(
    val listenerRegistered: Boolean,
    val listenerGeneration: Long,
    val listenerSupersessions: Long,
    val pendingRequests: Int,
    val requests: Long,
    val helloRequests: Long,
    val proveRequests: Long,
    val presentRequests: Long,
    val revokeRequests: Long,
    val challengesIssued: Long,
    val proofsAccepted: Long,
    val presentationsAccepted: Long,
    val revocationsAccepted: Long,
    val rejected: Long,
    val unavailable: Long,
    val timedOut: Long,
    val interrupted: Long,
    val closed: Boolean,
)

/**
 * Single-owner, single-pending-request rendezvous for the active-document protocol.
 *
 * Listener replacement creates a new generation and supersedes an older generation fail-closed.
 * A timeout, interruption, unregister, supersession, or close makes every late completion inert.
 * There is no queue, retry, polling, retained transcript, or secret-bearing metric.
 */
class ChromeMediaShieldActiveDocumentHandshake(
    private val waitTimeoutMillis: Long = DefaultWaitTimeoutMillis,
) : AutoCloseable {
    private val lock = Any()
    private var listenerRegistration: ListenerRegistration? = null
    private var listenerGeneration = 0L
    private var listenerSupersessions = 0L
    private var pendingRequest: PendingRequest? = null
    private var requests = 0L
    private var helloRequests = 0L
    private var proveRequests = 0L
    private var presentRequests = 0L
    private var revokeRequests = 0L
    private var challengesIssued = 0L
    private var proofsAccepted = 0L
    private var presentationsAccepted = 0L
    private var revocationsAccepted = 0L
    private var rejected = 0L
    private var unavailable = 0L
    private var timedOut = 0L
    private var interrupted = 0L
    private var closed = false

    init {
        require(waitTimeoutMillis in 1L..MaximumWaitTimeoutMillis)
    }

    /** Installs a new owner generation and supersedes an older owner, if any. */
    fun register(listener: ChromeMediaShieldActiveDocumentHandshakeListener): AutoCloseable {
        val orphaned: PendingRequest?
        val generation: Long
        synchronized(lock) {
            check(!closed) { "Active-document handshake is closed" }
            if (listenerRegistration != null) listenerSupersessions = listenerSupersessions.incremented()
            check(listenerGeneration < Long.MAX_VALUE) { "Active-document listener generation exhausted" }
            listenerGeneration += 1L
            generation = listenerGeneration
            listenerRegistration = ListenerRegistration(generation, listener)
            orphaned = pendingRequest?.takeIf { it.listenerGeneration != generation }
        }
        orphaned?.complete(ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable)
        return RegistrationHandle(generation)
    }

    /** Dispatches one phase and blocks within the fixed bound for its one-shot terminal result. */
    fun await(request: ChromeMediaShieldActiveDocumentRequest): ChromeMediaShieldActiveDocumentHandshakeResult {
        val registration: ListenerRegistration
        val pending: PendingRequest
        val superseded: PendingRequest?
        synchronized(lock) {
            recordRequestLocked(request.phase)
            registration = listenerRegistration ?: return unavailableLocked()
            if (closed) return unavailableLocked()
            val currentPending = pendingRequest
            if (
                currentPending != null &&
                (
                    request !is ChromeMediaShieldActiveDocumentRequest.Hello ||
                        currentPending.belongsToGeneration(request.claim)
                )
            ) {
                return unavailableLocked()
            }
            superseded = currentPending
            pending =
                PendingRequest(
                    owner = this,
                    listenerGeneration = registration.generation,
                    requestClaim = request.claim,
                    requestPhase = request.phase,
                    waitTimeoutMillis = waitTimeoutMillis,
                )
            pendingRequest = pending
        }

        superseded?.complete(ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable)

        try {
            registration.listener.onActiveDocumentRequest(request, pending)
        } catch (_: Throwable) {
            pending.reject()
        }
        return pending.await()
    }

    fun snapshot(): ChromeMediaShieldActiveDocumentHandshakeSnapshot =
        synchronized(lock) {
            ChromeMediaShieldActiveDocumentHandshakeSnapshot(
                listenerRegistered = listenerRegistration != null,
                listenerGeneration = listenerGeneration,
                listenerSupersessions = listenerSupersessions,
                pendingRequests = if (pendingRequest == null) 0 else 1,
                requests = requests,
                helloRequests = helloRequests,
                proveRequests = proveRequests,
                presentRequests = presentRequests,
                revokeRequests = revokeRequests,
                challengesIssued = challengesIssued,
                proofsAccepted = proofsAccepted,
                presentationsAccepted = presentationsAccepted,
                revocationsAccepted = revocationsAccepted,
                rejected = rejected,
                unavailable = unavailable,
                timedOut = timedOut,
                interrupted = interrupted,
                closed = closed,
            )
        }

    override fun close() {
        val orphaned: PendingRequest?
        synchronized(lock) {
            if (closed) return
            closed = true
            listenerRegistration = null
            orphaned = pendingRequest
        }
        orphaned?.complete(ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable)
    }

    private fun unregister(generation: Long) {
        val orphaned: PendingRequest?
        synchronized(lock) {
            if (listenerRegistration?.generation != generation) return
            listenerRegistration = null
            orphaned = pendingRequest?.takeIf { it.listenerGeneration == generation }
        }
        orphaned?.complete(ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable)
    }

    private fun completeFromListener(
        request: PendingRequest,
        proposed: ChromeMediaShieldActiveDocumentHandshakeResult,
    ): Boolean {
        val current =
            synchronized(lock) {
                !closed &&
                    listenerRegistration?.generation == request.listenerGeneration &&
                    pendingRequest === request
            }
        // Timeout/cancellation publishes while holding PendingRequest first, so never acquire that
        // monitor while retaining the owner monitor.
        return current && request.completeFromCurrentListener(proposed.forPhase(request.requestPhase))
    }

    private fun onTerminal(
        request: PendingRequest,
        result: ChromeMediaShieldActiveDocumentHandshakeResult,
    ) {
        synchronized(lock) {
            if (pendingRequest === request) pendingRequest = null
            when (result) {
                is ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued ->
                    challengesIssued = challengesIssued.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.ProofAccepted ->
                    proofsAccepted = proofsAccepted.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.PresentationAccepted ->
                    presentationsAccepted = presentationsAccepted.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.Revoked ->
                    revocationsAccepted = revocationsAccepted.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.Rejected -> rejected = rejected.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable -> unavailable = unavailable.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut -> timedOut = timedOut.incremented()
                ChromeMediaShieldActiveDocumentHandshakeResult.Interrupted -> interrupted = interrupted.incremented()
            }
        }
    }

    private fun recordRequestLocked(phase: ChromeMediaShieldActiveDocumentPhase) {
        requests = requests.incremented()
        when (phase) {
            ChromeMediaShieldActiveDocumentPhase.Hello -> helloRequests = helloRequests.incremented()
            ChromeMediaShieldActiveDocumentPhase.Prove -> proveRequests = proveRequests.incremented()
            ChromeMediaShieldActiveDocumentPhase.Present -> presentRequests = presentRequests.incremented()
            ChromeMediaShieldActiveDocumentPhase.Revoke -> revokeRequests = revokeRequests.incremented()
        }
    }

    private fun unavailableLocked(): ChromeMediaShieldActiveDocumentHandshakeResult {
        unavailable = unavailable.incremented()
        return ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable
    }

    private fun ChromeMediaShieldActiveDocumentHandshakeResult.forPhase(
        phase: ChromeMediaShieldActiveDocumentPhase,
    ): ChromeMediaShieldActiveDocumentHandshakeResult {
        val valid =
            when (phase) {
                ChromeMediaShieldActiveDocumentPhase.Hello ->
                    this is ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued
                ChromeMediaShieldActiveDocumentPhase.Prove ->
                    this == ChromeMediaShieldActiveDocumentHandshakeResult.ProofAccepted
                ChromeMediaShieldActiveDocumentPhase.Present ->
                    this == ChromeMediaShieldActiveDocumentHandshakeResult.PresentationAccepted
                ChromeMediaShieldActiveDocumentPhase.Revoke ->
                    this == ChromeMediaShieldActiveDocumentHandshakeResult.Revoked
            }
        return if (valid || this == ChromeMediaShieldActiveDocumentHandshakeResult.Rejected) {
            this
        } else {
            ChromeMediaShieldActiveDocumentHandshakeResult.Rejected
        }
    }

    private fun Long.incremented(): Long = if (this == Long.MAX_VALUE) this else this + 1L

    private data class ListenerRegistration(
        val generation: Long,
        val listener: ChromeMediaShieldActiveDocumentHandshakeListener,
    )

    private inner class RegistrationHandle(
        private val generation: Long,
    ) : AutoCloseable {
        private var handleClosed = false

        override fun close() {
            synchronized(this) {
                if (handleClosed) return
                handleClosed = true
            }
            unregister(generation)
        }
    }

    private class PendingRequest(
        private val owner: ChromeMediaShieldActiveDocumentHandshake,
        val listenerGeneration: Long,
        private val requestClaim: ChromeMediaShieldReadyClaim,
        val requestPhase: ChromeMediaShieldActiveDocumentPhase,
        waitTimeoutMillis: Long,
    ) : ChromeMediaShieldActiveDocumentHandshakeCompletion {
        private val latch = CountDownLatch(1)
        private val timeoutMillis = waitTimeoutMillis
        private val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(waitTimeoutMillis)
        private val startedAtNanos = System.nanoTime()
        private var result: ChromeMediaShieldActiveDocumentHandshakeResult? = null
        private var transportCancellationCallback: (() -> Unit)? = null

        override fun onTransportCancelled(
            callback: () -> Unit,
        ): ChromeMediaShieldActiveDocumentTransportCancellationRegistration {
            val registration: ChromeMediaShieldActiveDocumentTransportCancellationRegistration
            synchronized(this) {
                val terminal = result
                if (terminal == null) {
                    check(transportCancellationCallback == null) {
                        "Transport cancellation cleanup is already registered"
                    }
                    transportCancellationCallback = callback
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

        fun belongsToGeneration(claim: ChromeMediaShieldReadyClaim): Boolean = requestClaim == claim

        override fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(challenge),
            )

        override fun acceptProof(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldActiveDocumentHandshakeResult.ProofAccepted,
            )

        override fun acceptPresentation(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldActiveDocumentHandshakeResult.PresentationAccepted,
            )

        override fun acceptRevocation(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldActiveDocumentHandshakeResult.Revoked,
            )

        override fun reject(): Boolean =
            owner.completeFromListener(
                this,
                ChromeMediaShieldActiveDocumentHandshakeResult.Rejected,
            )

        fun completeFromCurrentListener(proposed: ChromeMediaShieldActiveDocumentHandshakeResult): Boolean {
            val completedBeforeDeadline: Boolean
            val terminal: ChromeMediaShieldActiveDocumentHandshakeResult
            val cancellationCallback: (() -> Unit)?
            synchronized(this) {
                if (result != null) return false
                completedBeforeDeadline = System.nanoTime() - startedAtNanos < timeoutNanos
                terminal =
                    if (completedBeforeDeadline) {
                        proposed
                    } else {
                        ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut
                    }
                result = terminal
                cancellationCallback = takeTransportCancellationCallbackLocked(terminal)
            }
            try {
                owner.onTerminal(this, terminal)
                cancellationCallback?.let { runCatching(it) }
            } finally {
                // Wake the network waiter only after the owner no longer exposes this request as
                // pending; the next synchronous PROVE/PRESENT must never race stale admission.
                latch.countDown()
            }
            return completedBeforeDeadline
        }

        fun complete(terminal: ChromeMediaShieldActiveDocumentHandshakeResult): Boolean {
            val cancellationCallback: (() -> Unit)?
            synchronized(this) {
                if (result != null) return false
                result = terminal
                cancellationCallback = takeTransportCancellationCallbackLocked(terminal)
            }
            try {
                owner.onTerminal(this, terminal)
                cancellationCallback?.let { runCatching(it) }
            } finally {
                latch.countDown()
            }
            return true
        }

        fun await(): ChromeMediaShieldActiveDocumentHandshakeResult {
            try {
                if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    if (!complete(ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut)) {
                        latch.await()
                    }
                }
            } catch (_: InterruptedException) {
                complete(ChromeMediaShieldActiveDocumentHandshakeResult.Interrupted)
                Thread.currentThread().interrupt()
            }
            return synchronized(this) { checkNotNull(result) }
        }

        private fun takeTransportCancellationCallbackLocked(
            terminal: ChromeMediaShieldActiveDocumentHandshakeResult,
        ): (() -> Unit)? {
            val callback = transportCancellationCallback
            transportCancellationCallback = null
            return callback.takeIf { terminal.isTransportCancellation() }
        }

        private fun ChromeMediaShieldActiveDocumentHandshakeResult.isTransportCancellation(): Boolean =
            this == ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut ||
                this == ChromeMediaShieldActiveDocumentHandshakeResult.Interrupted ||
                this == ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable
    }

    private companion object {
        const val DefaultWaitTimeoutMillis = 5_000L
        const val MaximumWaitTimeoutMillis = 10_000L
    }
}

/** Process-local rendezvous shared by the DEV proxy and native presentation owner. */
object ChromeMediaShieldActiveDocumentHandshakeBridge {
    private val handshake = ChromeMediaShieldActiveDocumentHandshake()

    fun register(listener: ChromeMediaShieldActiveDocumentHandshakeListener): AutoCloseable =
        handshake.register(listener)

    fun await(request: ChromeMediaShieldActiveDocumentRequest): ChromeMediaShieldActiveDocumentHandshakeResult =
        handshake.await(request)

    fun snapshot(): ChromeMediaShieldActiveDocumentHandshakeSnapshot = handshake.snapshot()
}
