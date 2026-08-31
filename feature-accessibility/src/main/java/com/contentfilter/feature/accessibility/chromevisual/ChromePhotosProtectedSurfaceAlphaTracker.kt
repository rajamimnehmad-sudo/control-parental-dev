package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromePhotosProtectedSurfaceAlpha {
    Opaque,
    Transparent,
}

internal data class ChromePhotosProtectedSurfaceAlphaToken(
    val sequence: Long,
    val target: ChromePhotosProtectedSurfaceAlpha,
)

internal data class ChromePhotosProtectedSurfaceAlphaSnapshot(
    val mayBeTransparent: Boolean,
    val pendingTransitions: Int,
    val submitFailures: Long,
)

/**
 * Conservative bookkeeping for asynchronous SurfaceControl alpha transactions.
 *
 * A rejected presentation capability cannot cancel an alpha transaction that Android already
 * accepted. Consequently, the surface remains "possibly transparent" until a later opaque
 * transaction is actually committed. Sequence ordering makes a late callback from an older
 * transparent transaction permanently unable to override a newer committed opaque transition.
 */
internal class ChromePhotosProtectedSurfaceAlphaTracker {
    private var nextSequence = 0L
    private var lastCommittedSequence = 0L
    private var committedAlpha = ChromePhotosProtectedSurfaceAlpha.Opaque
    private val pending = linkedMapOf<Long, ChromePhotosProtectedSurfaceAlpha>()
    private var uncertain = false
    private var submitFailures = 0L

    fun begin(target: ChromePhotosProtectedSurfaceAlpha): ChromePhotosProtectedSurfaceAlphaToken {
        check(nextSequence < Long.MAX_VALUE) { "Protected-surface alpha sequence exhausted" }
        nextSequence += 1L
        pending[nextSequence] = target
        return ChromePhotosProtectedSurfaceAlphaToken(nextSequence, target)
    }

    fun submittedWithoutCallback(token: ChromePhotosProtectedSurfaceAlphaToken) {
        commit(token)
    }

    fun commit(token: ChromePhotosProtectedSurfaceAlphaToken) {
        if (token.sequence > lastCommittedSequence) {
            lastCommittedSequence = token.sequence
            committedAlpha = token.target
        }
        pending.keys.removeAll { sequence -> sequence <= token.sequence }
        if (
            token.target == ChromePhotosProtectedSurfaceAlpha.Opaque &&
            token.sequence >= lastCommittedSequence
        ) {
            uncertain = false
        }
    }

    fun submissionFailed(token: ChromePhotosProtectedSurfaceAlphaToken) {
        pending.remove(token.sequence)
        submitFailures = submitFailures.incremented()
        if (
            token.target == ChromePhotosProtectedSurfaceAlpha.Opaque &&
            (
                committedAlpha == ChromePhotosProtectedSurfaceAlpha.Transparent ||
                    pending.values.any { it == ChromePhotosProtectedSurfaceAlpha.Transparent }
            )
        ) {
            uncertain = true
        }
    }

    fun reset() {
        pending.clear()
        lastCommittedSequence = nextSequence
        committedAlpha = ChromePhotosProtectedSurfaceAlpha.Opaque
        uncertain = false
    }

    fun snapshot(): ChromePhotosProtectedSurfaceAlphaSnapshot =
        ChromePhotosProtectedSurfaceAlphaSnapshot(
            mayBeTransparent =
                uncertain ||
                    committedAlpha == ChromePhotosProtectedSurfaceAlpha.Transparent ||
                    pending.values.any { it == ChromePhotosProtectedSurfaceAlpha.Transparent },
            pendingTransitions = pending.size,
            submitFailures = submitFailures,
        )

    private fun Long.incremented(): Long = if (this == Long.MAX_VALUE) this else this + 1L
}
