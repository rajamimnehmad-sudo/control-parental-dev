package com.contentfilter.feature.accessibility.chromevisual

/** Monotonic ownership token for one submitted transparent transaction. */
internal data class ChromePhotosTransparentCommitToken(
    val sequence: Long,
)

internal enum class ChromePhotosTransparentCommitOutcome {
    Committed,
    RejectedCurrent,
    Stale,
}

/**
 * Keeps transaction submission separate from committed presentation authority.
 *
 * A caller receives success only from [onTransactionCommitted]. Invalidating a pending attempt
 * completes it fail-closed immediately and makes any later platform callback permanently inert.
 * The gate is main-thread confined by [ChromePhotosProtectedSurface].
 */
internal class ChromePhotosTransparentCommitGate {
    private var nextSequence = 0L
    private var pending: Pending? = null

    fun begin(onResult: (Boolean) -> Unit): ChromePhotosTransparentCommitToken {
        invalidate()
        val token = ChromePhotosTransparentCommitToken(++nextSequence)
        pending = Pending(token, onResult)
        return token
    }

    fun reject(token: ChromePhotosTransparentCommitToken): Boolean =
        take(token)?.let { attempt ->
            attempt.complete(false)
            true
        } ?: false

    fun invalidate(): Boolean {
        val attempt = pending ?: return false
        pending = null
        attempt.complete(false)
        return true
    }

    fun onTransactionCommitted(
        token: ChromePhotosTransparentCommitToken,
        boundaryCurrent: () -> Boolean,
        commitCurrent: () -> Boolean,
    ): ChromePhotosTransparentCommitOutcome {
        val attempt = take(token) ?: return ChromePhotosTransparentCommitOutcome.Stale
        val committed =
            runCatching {
                boundaryCurrent() && commitCurrent()
            }.getOrDefault(false)
        attempt.complete(committed)
        return if (committed) {
            ChromePhotosTransparentCommitOutcome.Committed
        } else {
            ChromePhotosTransparentCommitOutcome.RejectedCurrent
        }
    }

    private fun take(token: ChromePhotosTransparentCommitToken): Pending? {
        val attempt = pending?.takeIf { it.token == token } ?: return null
        pending = null
        return attempt
    }

    private data class Pending(
        val token: ChromePhotosTransparentCommitToken,
        val onResult: (Boolean) -> Unit,
    ) {
        fun complete(result: Boolean) {
            runCatching { onResult(result) }
        }
    }
}
