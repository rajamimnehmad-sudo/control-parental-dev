package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

internal enum class ChromeMediaShieldReadyReleaseAction {
    Ignore,
    AttemptRelease,
    Revoke,
}

internal data class ChromeMediaShieldReadyViewportTransition(
    val retainCurrentDocument: Boolean,
) {
    val revokeBeforePrepare: Boolean = !retainCurrentDocument
}

/**
 * Keeps the READY document only for an exact same-window viewport replacement.
 *
 * The caller must apply [ChromeMediaShieldReadyViewportTransition.revokeBeforePrepare] before it
 * asks the coordinator to prepare the replacement surface. This makes it impossible to clear the
 * focus binding and then accidentally ask the coordinator to retain it.
 */
internal object ChromeMediaShieldReadyViewportTransitionPolicy {
    fun decide(
        currentActive: Boolean,
        currentWindowId: Int,
        currentViewport: ChromeVisualViewport?,
        nextWindowId: Int,
        nextViewport: ChromeVisualViewport,
    ): ChromeMediaShieldReadyViewportTransition =
        ChromeMediaShieldReadyViewportTransition(
            retainCurrentDocument =
                currentActive &&
                    currentWindowId == nextWindowId &&
                    currentViewport != null &&
                    currentViewport != nextViewport,
        )
}

/** Retains state only for a released document whose exact event-bound anchor can be reverified. */
internal object ChromeMediaShieldReadyContinuityReleasePolicy {
    fun canRetainReleasedDocument(
        retainCurrentDocument: Boolean,
        releasedClaimCurrent: Boolean,
        activeLeasePresent: Boolean,
        activeDocumentPresent: Boolean,
    ): Boolean =
        retainCurrentDocument &&
            releasedClaimCurrent &&
            activeLeasePresent &&
            activeDocumentPresent

    fun acceptsBoundary(binding: ChromeMediaShieldBoundContextBinding): Boolean =
        binding == ChromeMediaShieldBoundContextBinding.ExactEventSource
}

/**
 * Small state-driven ordering gate for the H19 event-source authority.
 *
 * It never validates browser evidence itself. It only makes the coordinator's one-shot ordering
 * explicit: the same claim needs both an opaque surface commit and an exact focused source before
 * a boundary release may be attempted.
 */
internal class ChromeMediaShieldReadyEventReleaseGate {
    private var activeClaim: ChromeMediaShieldReadyClaim? = null
    private var opaqueCommitted = false
    private var eventBound = false
    private var released = false
    private var closed = false

    fun onClaim(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyReleaseAction {
        if (closed) return ChromeMediaShieldReadyReleaseAction.Ignore
        val replaced = activeClaim != null
        activeClaim = claim
        opaqueCommitted = false
        eventBound = false
        released = false
        return if (replaced) ChromeMediaShieldReadyReleaseAction.Revoke else ChromeMediaShieldReadyReleaseAction.Ignore
    }

    fun onOpaqueCommitted(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyReleaseAction {
        if (closed || claim != activeClaim) return ChromeMediaShieldReadyReleaseAction.Ignore
        opaqueCommitted = true
        return actionForCurrent(claim)
    }

    fun onEventBound(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyReleaseAction {
        if (closed || claim != activeClaim) return ChromeMediaShieldReadyReleaseAction.Ignore
        eventBound = true
        return actionForCurrent(claim)
    }

    fun onSurfaceInvalidated(retainFocus: Boolean): ChromeMediaShieldReadyReleaseAction {
        if (closed || activeClaim == null) return ChromeMediaShieldReadyReleaseAction.Ignore
        opaqueCommitted = false
        released = false
        if (!retainFocus) eventBound = false
        return ChromeMediaShieldReadyReleaseAction.Revoke
    }

    fun canAttemptRelease(claim: ChromeMediaShieldReadyClaim): Boolean =
        !closed &&
            claim == activeClaim &&
            opaqueCommitted &&
            eventBound &&
            !released

    /**
     * Consumes the one-shot only after the exact boundary commit succeeds.
     *
     * A failed registry/surface commit remains fail-closed and pending. It may be attempted again
     * only when a later state-driven opaque/focus event reaches the coordinator; this method never
     * schedules or loops by itself.
     */
    fun commitRelease(
        claim: ChromeMediaShieldReadyClaim,
        commit: () -> Boolean,
    ): Boolean {
        if (!canAttemptRelease(claim)) return false
        if (!commit()) return false
        released = true
        return true
    }

    fun forget(): ChromeMediaShieldReadyReleaseAction {
        val revoke = activeClaim != null || released
        activeClaim = null
        opaqueCommitted = false
        eventBound = false
        released = false
        return if (revoke) ChromeMediaShieldReadyReleaseAction.Revoke else ChromeMediaShieldReadyReleaseAction.Ignore
    }

    fun close(): ChromeMediaShieldReadyReleaseAction {
        if (closed) return ChromeMediaShieldReadyReleaseAction.Ignore
        val action = forget()
        closed = true
        return action
    }

    private fun actionForCurrent(claim: ChromeMediaShieldReadyClaim): ChromeMediaShieldReadyReleaseAction =
        if (canAttemptRelease(claim)) {
            ChromeMediaShieldReadyReleaseAction.AttemptRelease
        } else {
            ChromeMediaShieldReadyReleaseAction.Ignore
        }
}
