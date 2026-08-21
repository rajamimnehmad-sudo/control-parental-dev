package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromePhotosProtectedSurfacePhase {
    Inactive,
    Covered,
    Motion,
    Settling,
    Capturing,
    CommitReady,
    Presented,
}

internal data class ChromePhotosProtectedSurfaceToken(
    val epoch: Long,
    val windowId: Int,
    val sequence: Long,
    val viewport: ChromeVisualViewport,
)

internal data class ChromePhotosProtectedSurfaceSnapshot(
    val phase: ChromePhotosProtectedSurfacePhase,
    val epoch: Long,
    val windowId: Int,
    val viewport: ChromeVisualViewport?,
    val activeSequence: Long,
    val presentedSequence: Long,
) {
    val isActive: Boolean get() = phase != ChromePhotosProtectedSurfacePhase.Inactive
}

/**
 * Monotonic authority for the DEV protected-surface spike.
 *
 * Epochs and capture sequences are never reset, including after disarm. This deliberately avoids
 * ABA reuse: work created before a Chrome exit can never become current after a later re-entry.
 */
internal class ChromePhotosProtectedSurfaceState {
    private var phase = ChromePhotosProtectedSurfacePhase.Inactive
    private var epoch = 0L
    private var nextSequence = 0L
    private var activeSequence = 0L
    private var presentedSequence = 0L
    private var windowId = InvalidWindowId
    private var viewport: ChromeVisualViewport? = null

    @Synchronized
    fun arm(
        windowId: Int,
        viewport: ChromeVisualViewport,
    ): ChromePhotosProtectedSurfaceSnapshot {
        if (
            phase == ChromePhotosProtectedSurfacePhase.Inactive ||
            this.windowId != windowId ||
            this.viewport != viewport
        ) {
            epoch += 1L
            this.windowId = windowId
            this.viewport = viewport
            activeSequence = 0L
            phase = ChromePhotosProtectedSurfacePhase.Covered
        }
        return snapshotLocked()
    }

    @Synchronized
    fun invalidate(
        windowId: Int,
        viewport: ChromeVisualViewport,
        motion: Boolean,
    ): ChromePhotosProtectedSurfaceSnapshot {
        epoch += 1L
        this.windowId = windowId
        this.viewport = viewport
        activeSequence = 0L
        phase =
            if (motion) {
                ChromePhotosProtectedSurfacePhase.Motion
            } else {
                ChromePhotosProtectedSurfacePhase.Covered
            }
        return snapshotLocked()
    }

    @Synchronized
    fun markSettling(expectedEpoch: Long): Boolean {
        if (!matchesEpoch(expectedEpoch)) return false
        phase = ChromePhotosProtectedSurfacePhase.Settling
        return true
    }

    @Synchronized
    fun beginCapture(expectedEpoch: Long): ChromePhotosProtectedSurfaceToken? {
        val currentViewport = viewport ?: return null
        if (!matchesEpoch(expectedEpoch)) return null
        nextSequence += 1L
        activeSequence = nextSequence
        phase = ChromePhotosProtectedSurfacePhase.Capturing
        return ChromePhotosProtectedSurfaceToken(
            epoch = epoch,
            windowId = windowId,
            sequence = activeSequence,
            viewport = currentViewport,
        )
    }

    @Synchronized
    fun markCommitReady(token: ChromePhotosProtectedSurfaceToken): Boolean {
        if (!isCurrent(token)) return false
        phase = ChromePhotosProtectedSurfacePhase.CommitReady
        return true
    }

    @Synchronized
    fun markPresented(token: ChromePhotosProtectedSurfaceToken): Boolean {
        if (!isCurrent(token) || phase != ChromePhotosProtectedSurfacePhase.CommitReady) return false
        presentedSequence = token.sequence
        phase = ChromePhotosProtectedSurfacePhase.Presented
        return true
    }

    @Synchronized
    fun fail(token: ChromePhotosProtectedSurfaceToken): Boolean {
        if (!isCurrent(token)) return false
        phase = ChromePhotosProtectedSurfacePhase.Covered
        return true
    }

    @Synchronized
    fun disarm(): ChromePhotosProtectedSurfaceSnapshot {
        if (phase != ChromePhotosProtectedSurfacePhase.Inactive) epoch += 1L
        phase = ChromePhotosProtectedSurfacePhase.Inactive
        windowId = InvalidWindowId
        viewport = null
        activeSequence = 0L
        return snapshotLocked()
    }

    @Synchronized
    fun snapshot(): ChromePhotosProtectedSurfaceSnapshot = snapshotLocked()

    private fun matchesEpoch(expectedEpoch: Long): Boolean =
        phase != ChromePhotosProtectedSurfacePhase.Inactive && epoch == expectedEpoch

    private fun isCurrent(token: ChromePhotosProtectedSurfaceToken): Boolean =
        phase != ChromePhotosProtectedSurfacePhase.Inactive &&
            token.epoch == epoch &&
            token.windowId == windowId &&
            token.sequence == activeSequence &&
            token.viewport == viewport

    private fun snapshotLocked() =
        ChromePhotosProtectedSurfaceSnapshot(
            phase = phase,
            epoch = epoch,
            windowId = windowId,
            viewport = viewport,
            activeSequence = activeSequence,
            presentedSequence = presentedSequence,
        )

    private companion object {
        const val InvalidWindowId = -1
    }
}
