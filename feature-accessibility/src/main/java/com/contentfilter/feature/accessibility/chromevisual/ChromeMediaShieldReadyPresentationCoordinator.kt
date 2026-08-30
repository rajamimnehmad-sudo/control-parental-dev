package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeListener

internal data class ChromeMediaShieldReadyPresentationTarget(
    val claim: ChromeMediaShieldReadyClaim,
    val surface: ChromePhotosProtectedSurfaceSnapshot,
    val opaqueCommitted: Boolean = false,
)

internal object ChromeMediaShieldReadyPresentationPolicy {
    fun acceptsAttestation(
        claim: ChromeMediaShieldReadyClaim,
        attestation: ChromePhotosDataPlaneAttestation,
        now: Long,
    ): Boolean =
        claim.identity.topLevel &&
            attestation.mediaAuthorityEnabled &&
            attestation.mediaPolicyEpoch > 0L &&
            attestation.sessionId == claim.identity.protectionSessionId &&
            attestation.mediaPolicyEpoch == claim.identity.policyEpoch &&
            attestation.isPresentationEligible(now)

    fun isExactBoundary(
        target: ChromeMediaShieldReadyPresentationTarget,
        currentSurface: ChromePhotosProtectedSurfaceSnapshot,
        windowId: Int,
        viewport: ChromeVisualViewport,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean =
        target.opaqueCommitted &&
            target.surface == currentSurface &&
            target.surface.windowId == windowId &&
            target.surface.viewport == viewport &&
            document.windowId == windowId &&
            document.identity == target.claim.identity &&
            document.lifecycleSequence == target.claim.lifecycleSequence

    fun isAwaitingCurrentMarker(
        target: ChromeMediaShieldReadyPresentationTarget,
        currentSurface: ChromePhotosProtectedSurfaceSnapshot,
        windowId: Int,
        viewport: ChromeVisualViewport,
        claimCurrent: Boolean,
        attestationAccepted: Boolean,
        completionPending: Boolean,
        released: Boolean,
        hasLease: Boolean,
        surfaceTransparent: Boolean,
    ): Boolean =
        claimCurrent &&
            attestationAccepted &&
            (completionPending || target.opaqueCommitted) &&
            !released &&
            !hasLease &&
            !surfaceTransparent &&
            target.surface == currentSurface &&
            target.surface.windowId == windowId &&
            target.surface.viewport == viewport

    fun requiresImmediateRevocation(
        released: Boolean,
        presentationStillVerified: Boolean,
    ): Boolean = released && !presentationStillVerified
}

/**
 * H19's non-raster foreground boundary.
 *
 * READY only acknowledges after the current Chrome window is covered by an opaque transaction.
 * The surface becomes transparent later, and only after the exact claimed token+lifecycle appears
 * in that same unique foreground Accessibility window and every boundary is rechecked.
 */
internal class ChromeMediaShieldReadyPresentationCoordinator(
    private val service: AccessibilityService,
    private val state: ChromePhotosProtectedSurfaceState,
    private val surface: ChromePhotosProtectedSurface,
    private val windowInspector: ChromeVisualWindowInspector,
    private val tokenScanner: ChromeMediaShieldAccessibilityTokenScanner,
    private val attestationReader: ChromePhotosDataPlaneAttestationReader,
    private val onLegacyWorkCancelled: () -> Unit,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val leaseAuthority = ChromePhotosDataPlaneLeaseAuthority()
    private val releaseGate = ChromeMediaShieldReadyEventReleaseGate()
    private val registration =
        ChromeMediaShieldReadyHandshakeBridge.register(
            ChromeMediaShieldReadyHandshakeListener(::onReadyClaim),
        )
    private val leaseWatchdog = Runnable(::verifyLeaseOnMain)
    private var activeClaim: ChromeMediaShieldReadyClaim? = null
    private var pendingCompletion: ChromeMediaShieldReadyHandshakeCompletion? = null
    private var target: ChromeMediaShieldReadyPresentationTarget? = null
    private var releasedTarget: ChromeMediaShieldReadyPresentationTarget? = null
    private var activeLease: ChromePhotosDataPlaneLease? = null
    private var activeDocument: ChromeMediaShieldForegroundDocument? = null
    private var focusedDocument: ChromeMediaShieldForegroundDocument? = null
    private var closed = false

    fun hasCurrentClaim(): Boolean = activeClaim != null && !closed

    fun prepareCoveredSnapshot(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        retainCurrentDocument: Boolean,
    ) {
        checkMainThread()
        val claim = activeClaim ?: return
        if (!retainCurrentDocument) {
            rejectAndForget("ready_document_invalidated")
            return
        }
        releaseGate.onSurfaceInvalidated(retainFocus = true)
        val retainedDocument = (activeDocument ?: focusedDocument).takeIf { retainCurrentDocument }
        revokeLease("surface_invalidated")
        focusedDocument = retainedDocument
        target = ChromeMediaShieldReadyPresentationTarget(claim, snapshot)
        releasedTarget = null
    }

    fun onOpaqueCommitted(snapshot: ChromePhotosProtectedSurfaceSnapshot) {
        checkMainThread()
        val expected = target ?: return
        if (expected.surface != snapshot || expected.claim != activeClaim) return
        val committed = expected.copy(opaqueCommitted = true)
        target = committed
        val completion = pendingCompletion
        if (completion != null) {
            val attestation = attestationReader.read()
            val window = windowInspector.findUniqueForeground()
            val viewport = window?.let(windowInspector::viewport)
            val accepted =
                window != null &&
                    viewport != null &&
                    ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                        committed.claim,
                        attestation,
                        SystemClock.elapsedRealtime(),
                    ) &&
                    state.snapshot() == snapshot &&
                    window.id == snapshot.windowId &&
                    viewport == snapshot.viewport &&
                    completion.acceptAfterOpaqueCommit()
            pendingCompletion = null
            if (!accepted) {
                completion.reject()
                rejectAndForget("ready_ack_rejected")
                return
            }
            log("ready_ack_accepted", committed, null)
        }
        if (releaseGate.onOpaqueCommitted(committed.claim) == ChromeMediaShieldReadyReleaseAction.AttemptRelease) {
            tryReleaseCurrentBinding()
        }
    }

    fun onHostPublicationChanged(): Boolean {
        checkMainThread()
        val expected = target ?: return false
        if (expected.opaqueCommitted) return true
        val viewport = expected.surface.viewport ?: return true
        when (
            surface.cover(
                expected.surface.windowId,
                viewport,
                expected.surface.epoch,
                { committedEpoch ->
                    if (committedEpoch == expected.surface.epoch) onOpaqueCommitted(expected.surface)
                },
            )
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> rejectAndForget("ready_surface_failed")
            ChromePhotosProtectedSurfaceCoverResult.Pending -> Unit
            ChromePhotosProtectedSurfaceCoverResult.Ready -> Unit
        }
        return true
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        checkMainThread()
        val expected = target?.takeIf { it.opaqueCommitted } ?: return
        if (releasedTarget == expected) {
            val snapshot = state.snapshot()
            val viewport = expected.surface.viewport
            val document = activeDocument
            val stillVerified =
                viewport != null &&
                    document != null &&
                    exactForegroundDocument(
                        claim = expected.claim,
                        expectedWindowId = expected.surface.windowId,
                        document = document,
                        requireAnchor = true,
                    ) &&
                    hasVerifiedPresentation(
                        snapshot = snapshot,
                        viewport = viewport,
                        windowId = expected.surface.windowId,
                    )
            if (
                ChromeMediaShieldReadyPresentationPolicy.requiresImmediateRevocation(
                    released = true,
                    presentationStillVerified = stillVerified,
                )
            ) {
                releaseGate.onSurfaceInvalidated(retainFocus = false)
                revokeLease("ready_accessibility_observation_invalid", clearFocus = true)
            }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        val window = windowInspector.findUniqueForeground()
        if (window == null || window.id != expected.surface.windowId) return
        when (val result = tokenScanner.bindFocusedEvent(event, window, expected.claim)) {
            is ChromeMediaShieldTokenScanResult.Current -> {
                focusedDocument = result.document
                log("ready_focus_bound", expected, result.document)
                if (
                    releaseGate.onFocusBound(expected.claim) ==
                    ChromeMediaShieldReadyReleaseAction.AttemptRelease
                ) {
                    tryReleaseCurrentBinding()
                }
            }
            is ChromeMediaShieldTokenScanResult.FailClosed ->
                log("ready_focus_rejected", expected, null, result.reason)
        }
    }

    fun isAwaitingCurrentMarker(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        viewport: ChromeVisualViewport,
        windowId: Int,
    ): Boolean {
        checkMainThread()
        val expected = target ?: return false
        val attestation = attestationReader.read()
        return ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
            target = expected,
            currentSurface = snapshot,
            windowId = windowId,
            viewport = viewport,
            claimCurrent = expected.claim == activeClaim,
            attestationAccepted =
                ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                    expected.claim,
                    attestation,
                    SystemClock.elapsedRealtime(),
                ),
            completionPending = pendingCompletion != null,
            released = releasedTarget != null,
            hasLease = activeLease != null,
            surfaceTransparent = surface.stats().transparent,
        )
    }

    fun hasVerifiedPresentation(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        viewport: ChromeVisualViewport,
        windowId: Int,
    ): Boolean {
        checkMainThread()
        val expected = target ?: return false
        val lease = activeLease ?: return false
        val document = activeDocument ?: return false
        if (!exactForegroundDocument(expected.claim, windowId, document, requireAnchor = false)) return false
        val attestation = attestationReader.read()
        val context = snapshot.toLeaseContext(viewport, document)
        return surface.stats().transparent &&
            ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                expected.claim,
                attestation,
                SystemClock.elapsedRealtime(),
            ) &&
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                expected,
                snapshot,
                windowId,
                viewport,
                document,
            ) &&
            document == activeDocument &&
            leaseAuthority.isValid(lease, attestation, context)
    }

    fun revokePresentation(
        reason: String,
        forgetClaim: Boolean = false,
    ) {
        checkMainThread()
        if (forgetClaim) {
            releaseGate.forget()
        } else {
            releaseGate.onSurfaceInvalidated(retainFocus = false)
        }
        revokeLease(reason, clearFocus = true)
        if (forgetClaim) {
            pendingCompletion?.reject()
            pendingCompletion = null
            target = null
            releasedTarget = null
            activeClaim = null
            focusedDocument = null
        }
    }

    override fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            service.mainExecutor.execute(::close)
            return
        }
        if (closed) return
        closed = true
        releaseGate.close()
        registration.close()
        revokePresentation("ready_coordinator_closed", forgetClaim = true)
    }

    private fun onReadyClaim(
        claim: ChromeMediaShieldReadyClaim,
        completion: ChromeMediaShieldReadyHandshakeCompletion,
    ) {
        service.mainExecutor.execute { acceptClaimOnMain(claim, completion) }
    }

    private fun acceptClaimOnMain(
        claim: ChromeMediaShieldReadyClaim,
        completion: ChromeMediaShieldReadyHandshakeCompletion,
    ) {
        checkMainThread()
        if (closed) {
            completion.reject()
            return
        }
        val attestation = attestationReader.read()
        val window = windowInspector.findUniqueForeground()
        val viewport = window?.let(windowInspector::viewport)
        if (
            !ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                claim,
                attestation,
                SystemClock.elapsedRealtime(),
            ) ||
            window == null ||
            viewport == null
        ) {
            completion.reject()
            return
        }
        pendingCompletion?.reject()
        revokeLease("ready_claim_superseded", clearFocus = true)
        onLegacyWorkCancelled()
        releaseGate.onClaim(claim)
        activeClaim = claim
        pendingCompletion = completion
        releasedTarget = null
        focusedDocument = null
        val current = state.snapshot()
        val snapshot =
            if (!current.isActive) {
                state.arm(window.id, viewport)
            } else {
                state.invalidate(window.id, viewport, motion = false)
            }
        target = ChromeMediaShieldReadyPresentationTarget(claim, snapshot)
        when (
            surface.cover(
                window.id,
                viewport,
                snapshot.epoch,
                { committedEpoch ->
                    if (committedEpoch == snapshot.epoch) onOpaqueCommitted(snapshot)
                },
            )
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> rejectAndForget("ready_surface_failed")
            ChromePhotosProtectedSurfaceCoverResult.Pending ->
                log("ready_surface_pending", checkNotNull(target), null)
            ChromePhotosProtectedSurfaceCoverResult.Ready -> Unit
        }
    }

    private fun tryReleaseCurrentBinding(): Boolean {
        val expected = target ?: return false
        if (
            !expected.opaqueCommitted ||
            expected.claim != activeClaim ||
            !releaseGate.canAttemptRelease(expected.claim)
        ) {
            return false
        }
        val snapshot = state.snapshot()
        val viewport = expected.surface.viewport ?: return false
        if (releasedTarget == expected) {
            return hasVerifiedPresentation(
                snapshot = snapshot,
                viewport = viewport,
                windowId = expected.surface.windowId,
            )
        }
        val firstDocument = focusedDocument ?: return false
        if (
            !exactForegroundDocument(
                expected.claim,
                expected.surface.windowId,
                firstDocument,
                requireAnchor = false,
            )
        ) {
            return false
        }
        val firstAttestation = attestationReader.read()
        if (
            !ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                expected.claim,
                firstAttestation,
                SystemClock.elapsedRealtime(),
            ) ||
            !ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                expected,
                snapshot,
                expected.surface.windowId,
                viewport,
                firstDocument,
            )
        ) {
            return false
        }
        val firstContext = snapshot.toLeaseContext(viewport, firstDocument)
        val lease = leaseAuthority.mint(firstAttestation, firstContext) ?: return false

        val boundarySnapshot = state.snapshot()
        val boundaryDocument = focusedDocument
        val boundaryWindow = windowInspector.findUniqueForeground()
        val boundaryAnchorFailure =
            when {
                boundaryDocument == null -> "ready_boundary_document_missing"
                boundaryWindow == null -> "ready_boundary_window_missing"
                boundaryWindow.id != expected.surface.windowId -> "ready_boundary_window_mismatch"
                else -> tokenScanner.boundAnchorFailureReason(boundaryWindow, expected.claim, boundaryDocument)
            }
        if (boundaryAnchorFailure != null) {
            leaseAuthority.revoke()
            log("ready_release_rejected", expected, boundaryDocument, boundaryAnchorFailure)
            return false
        }
        val boundaryAttestation = attestationReader.read()
        val boundaryContext = boundaryDocument?.let { boundarySnapshot.toLeaseContext(viewport, it) }
        if (
            boundaryDocument == null ||
            boundaryContext == null ||
            boundaryDocument != firstDocument ||
            !ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                expected.claim,
                boundaryAttestation,
                SystemClock.elapsedRealtime(),
            ) ||
            !ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                expected,
                boundarySnapshot,
                expected.surface.windowId,
                viewport,
                boundaryDocument,
            ) ||
            !leaseAuthority.isValid(lease, boundaryAttestation, boundaryContext) ||
            !releaseGate.commitRelease(expected.claim) {
                ChromeMediaShieldDocumentAuthorityRegistry.commitIfClaimedForegroundCurrent(
                    claim = expected.claim,
                    accessibilityContext = boundaryDocument.accessibilityContext,
                ) {
                    surface.presentTransparent(lease)
                }
            }
        ) {
            leaseAuthority.revoke()
            surface.revokeTransparency()
            return false
        }
        activeLease = lease
        activeDocument = boundaryDocument
        releasedTarget = expected
        scheduleLeaseWatchdog()
        log("ready_foreground_released", expected, boundaryDocument)
        return true
    }

    private fun exactForegroundDocument(
        claim: ChromeMediaShieldReadyClaim,
        expectedWindowId: Int,
        document: ChromeMediaShieldForegroundDocument,
        requireAnchor: Boolean,
    ): Boolean {
        val window = windowInspector.findUniqueForeground() ?: return false
        if (window.id != expectedWindowId) return false
        return if (requireAnchor) {
            tokenScanner.verifiesBoundAnchor(window, claim, document)
        } else {
            tokenScanner.verifiesBoundContext(window, claim, document)
        }
    }

    private fun verifyLeaseOnMain() {
        val expected = target ?: return
        val snapshot = state.snapshot()
        val viewport = snapshot.viewport
        if (
            viewport == null ||
            !hasVerifiedPresentation(snapshot, viewport, snapshot.windowId)
        ) {
            releaseGate.onSurfaceInvalidated(retainFocus = false)
            revokeLease("ready_lease_stale_or_unhealthy", clearFocus = true)
            return
        }
        val lease = activeLease ?: return
        if (lease.validUntilElapsed - SystemClock.elapsedRealtime() <= LeaseRenewalLeadMillis) {
            val document = activeDocument ?: return revokeLease("ready_document_absent")
            val attestation = attestationReader.read()
            val context = snapshot.toLeaseContext(viewport, document)
            activeLease = leaseAuthority.mint(attestation, context)
            if (activeLease == null) {
                revokeLease("ready_lease_renewal_denied")
                return
            }
        }
        if (expected != target) {
            revokeLease("ready_target_changed")
            return
        }
        scheduleLeaseWatchdog()
    }

    private fun scheduleLeaseWatchdog() {
        mainHandler.removeCallbacks(leaseWatchdog)
        mainHandler.postDelayed(leaseWatchdog, LeaseWatchdogMillis)
    }

    private fun revokeLease(
        reason: String,
        clearFocus: Boolean = false,
    ) {
        mainHandler.removeCallbacks(leaseWatchdog)
        val hadLease = activeLease != null || surface.stats().transparent
        activeLease = null
        activeDocument = null
        if (clearFocus) {
            activeClaim?.let(ChromeMediaShieldDocumentAuthorityRegistry::deactivateClaimedForeground)
            focusedDocument = null
        }
        leaseAuthority.revoke()
        surface.revokeTransparency()
        if (hadLease) log("ready_foreground_revoked", target, null, reason)
    }

    private fun rejectAndForget(reason: String) {
        pendingCompletion?.reject()
        pendingCompletion = null
        releaseGate.forget()
        revokeLease(reason, clearFocus = true)
        target = null
        releasedTarget = null
        activeClaim = null
        focusedDocument = null
        log("ready_fail_closed", null, null, reason)
    }

    private fun ChromePhotosProtectedSurfaceSnapshot.toLeaseContext(
        viewport: ChromeVisualViewport,
        document: ChromeMediaShieldForegroundDocument,
    ) = ChromePhotosDataPlaneLeaseContext(
        packageName = ChromePackageName,
        windowId = windowId,
        epoch = epoch,
        viewport = viewport,
        foregroundDocument = document,
    )

    private fun log(
        phase: String,
        expected: ChromeMediaShieldReadyPresentationTarget?,
        document: ChromeMediaShieldForegroundDocument?,
        reason: String = "",
    ) {
        val claim = expected?.claim ?: activeClaim
        Log.i(
            LogTag,
            "phase=$phase windowId=${expected?.surface?.windowId ?: -1} " +
                "surfaceEpoch=${expected?.surface?.epoch ?: 0L} " +
                "documentSequence=${claim?.identity?.documentSequence ?: 0L} " +
                "lifecycle=${claim?.lifecycleSequence ?: 0L} " +
                "token=${claim?.identity?.tokenDigest?.take(TokenDigestLogLength).orEmpty()} " +
                "axBound=${document != null} binding=${if (document == null) "none" else "event_source"} " +
                "root=${document?.accessibilityContext?.rootIdentityDigest?.take(TokenDigestLogLength).orEmpty()} " +
                "source=${document?.accessibilityContext?.markerIdentityDigest?.take(TokenDigestLogLength).orEmpty()} " +
                "reason=$reason rawPresented=false",
        )
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper())
    }

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val TokenDigestLogLength = 12
        const val LeaseWatchdogMillis = 50L
        const val LeaseRenewalLeadMillis = 150L
        const val LogTag = "ChromeMediaShieldReady"
    }
}
