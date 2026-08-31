package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeMediaShieldActiveDocumentRevocationToken(
    val sequence: Long,
    val generation: Long,
)

internal enum class ChromeMediaShieldActiveDocumentRevocationDecision {
    Accepted,
    Rejected,
}

internal enum class ChromeMediaShieldActiveDocumentRevocationReason {
    OpaqueCommitted,
    AlreadyOpaque,
    SubmissionFailed,
    TimedOut,
    Cancelled,
    Superseded,
    Duplicate,
    Closed,
}

internal data class ChromeMediaShieldActiveDocumentRevocationResult(
    val decision: ChromeMediaShieldActiveDocumentRevocationDecision,
    val reason: ChromeMediaShieldActiveDocumentRevocationReason,
)

internal sealed interface ChromeMediaShieldActiveDocumentRevocationAdmission {
    val token: ChromeMediaShieldActiveDocumentRevocationToken?

    data class SubmitOpaque(
        override val token: ChromeMediaShieldActiveDocumentRevocationToken,
    ) : ChromeMediaShieldActiveDocumentRevocationAdmission

    data class AlreadyOpaque(
        override val token: ChromeMediaShieldActiveDocumentRevocationToken,
    ) : ChromeMediaShieldActiveDocumentRevocationAdmission

    data class Duplicate(
        override val token: ChromeMediaShieldActiveDocumentRevocationToken,
    ) : ChromeMediaShieldActiveDocumentRevocationAdmission

    data object Closed : ChromeMediaShieldActiveDocumentRevocationAdmission {
        override val token: ChromeMediaShieldActiveDocumentRevocationToken? = null
    }
}

internal enum class ChromeMediaShieldActiveDocumentRevocationSignalOutcome {
    Accepted,
    Rejected,
    Stale,
}

internal data class ChromeMediaShieldActiveDocumentRevocationSnapshot(
    val closed: Boolean,
    val nextSequence: Long,
    val pendingToken: ChromeMediaShieldActiveDocumentRevocationToken?,
    val lastTerminalToken: ChromeMediaShieldActiveDocumentRevocationToken?,
    val lastTerminalResult: ChromeMediaShieldActiveDocumentRevocationResult?,
)

/**
 * Bounded, main-thread state machine for the external-surface REVOKE boundary.
 *
 * A submitted alpha transition can grant a revocation acknowledgement only from its exact opaque
 * committed callback. Submission failure, transport timeout, cancellation, supersession, and late
 * callbacks remain fail-closed. [alreadyOpaque] is the only no-submit success path and must be
 * supplied from a conservative surface state that already proves opacity.
 *
 * The gate retains at most one pending callback and one terminal record. Callbacks are terminal,
 * one-shot, and deliberately isolated from state mutation failures.
 */
internal class ChromeMediaShieldActiveDocumentRevocationGate : AutoCloseable {
    private var nextSequence = 0L
    private var pending: Pending? = null
    private var lastTerminal: Terminal? = null
    private var closed = false

    fun begin(
        generation: Long,
        alreadyOpaque: Boolean,
        onResult: (ChromeMediaShieldActiveDocumentRevocationResult) -> Unit,
    ): ChromeMediaShieldActiveDocumentRevocationAdmission {
        require(generation > 0L) { "Revocation generation must be positive" }
        if (closed) {
            notify(
                onResult,
                rejected(ChromeMediaShieldActiveDocumentRevocationReason.Closed),
            )
            return ChromeMediaShieldActiveDocumentRevocationAdmission.Closed
        }

        val duplicateToken =
            pending?.token?.takeIf { it.generation == generation }
                ?: lastTerminal?.token?.takeIf { it.generation == generation }
        if (duplicateToken != null) {
            notify(
                onResult,
                rejected(ChromeMediaShieldActiveDocumentRevocationReason.Duplicate),
            )
            return ChromeMediaShieldActiveDocumentRevocationAdmission.Duplicate(duplicateToken)
        }

        supersedePending()
        check(nextSequence < Long.MAX_VALUE) { "Revocation sequence exhausted" }
        nextSequence += 1L
        val token = ChromeMediaShieldActiveDocumentRevocationToken(nextSequence, generation)
        if (alreadyOpaque) {
            val result = accepted(ChromeMediaShieldActiveDocumentRevocationReason.AlreadyOpaque)
            lastTerminal = Terminal(token, result)
            notify(onResult, result)
            return ChromeMediaShieldActiveDocumentRevocationAdmission.AlreadyOpaque(token)
        }

        pending = Pending(token, onResult)
        return ChromeMediaShieldActiveDocumentRevocationAdmission.SubmitOpaque(token)
    }

    fun onOpaqueCommitted(
        token: ChromeMediaShieldActiveDocumentRevocationToken,
    ): ChromeMediaShieldActiveDocumentRevocationSignalOutcome =
        complete(
            token,
            accepted(ChromeMediaShieldActiveDocumentRevocationReason.OpaqueCommitted),
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Accepted,
        )

    fun onSubmissionFailed(
        token: ChromeMediaShieldActiveDocumentRevocationToken,
    ): ChromeMediaShieldActiveDocumentRevocationSignalOutcome =
        complete(
            token,
            rejected(ChromeMediaShieldActiveDocumentRevocationReason.SubmissionFailed),
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
        )

    fun onTimedOut(
        token: ChromeMediaShieldActiveDocumentRevocationToken,
    ): ChromeMediaShieldActiveDocumentRevocationSignalOutcome =
        complete(
            token,
            rejected(ChromeMediaShieldActiveDocumentRevocationReason.TimedOut),
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
        )

    fun cancel(
        token: ChromeMediaShieldActiveDocumentRevocationToken,
    ): ChromeMediaShieldActiveDocumentRevocationSignalOutcome =
        complete(
            token,
            rejected(ChromeMediaShieldActiveDocumentRevocationReason.Cancelled),
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
        )

    fun snapshot(): ChromeMediaShieldActiveDocumentRevocationSnapshot =
        ChromeMediaShieldActiveDocumentRevocationSnapshot(
            closed = closed,
            nextSequence = nextSequence,
            pendingToken = pending?.token,
            lastTerminalToken = lastTerminal?.token,
            lastTerminalResult = lastTerminal?.result,
        )

    override fun close() {
        if (closed) return
        closed = true
        val current = pending ?: return
        pending = null
        val result = rejected(ChromeMediaShieldActiveDocumentRevocationReason.Cancelled)
        lastTerminal = Terminal(current.token, result)
        current.complete(result)
    }

    private fun complete(
        token: ChromeMediaShieldActiveDocumentRevocationToken,
        result: ChromeMediaShieldActiveDocumentRevocationResult,
        outcome: ChromeMediaShieldActiveDocumentRevocationSignalOutcome,
    ): ChromeMediaShieldActiveDocumentRevocationSignalOutcome {
        val current =
            pending?.takeIf { it.token == token }
                ?: return ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale
        pending = null
        lastTerminal = Terminal(token, result)
        current.complete(result)
        return outcome
    }

    private fun supersedePending() {
        val current = pending ?: return
        pending = null
        val result = rejected(ChromeMediaShieldActiveDocumentRevocationReason.Superseded)
        lastTerminal = Terminal(current.token, result)
        current.complete(result)
    }

    private data class Pending(
        val token: ChromeMediaShieldActiveDocumentRevocationToken,
        val onResult: (ChromeMediaShieldActiveDocumentRevocationResult) -> Unit,
    ) {
        fun complete(result: ChromeMediaShieldActiveDocumentRevocationResult) {
            notify(onResult, result)
        }
    }

    private data class Terminal(
        val token: ChromeMediaShieldActiveDocumentRevocationToken,
        val result: ChromeMediaShieldActiveDocumentRevocationResult,
    )

    private companion object {
        fun accepted(reason: ChromeMediaShieldActiveDocumentRevocationReason) =
            ChromeMediaShieldActiveDocumentRevocationResult(
                ChromeMediaShieldActiveDocumentRevocationDecision.Accepted,
                reason,
            )

        fun rejected(reason: ChromeMediaShieldActiveDocumentRevocationReason) =
            ChromeMediaShieldActiveDocumentRevocationResult(
                ChromeMediaShieldActiveDocumentRevocationDecision.Rejected,
                reason,
            )

        fun notify(
            callback: (ChromeMediaShieldActiveDocumentRevocationResult) -> Unit,
            result: ChromeMediaShieldActiveDocumentRevocationResult,
        ) {
            runCatching { callback(result) }
        }
    }
}
