package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeListener
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import java.security.SecureRandom

/**
 * H19 active-document authority. Browser pixels never participate in this boundary.
 *
 * The document capability proves the transformed bootstrap; Chrome's unique foreground native
 * window/root proves the presentation context. A challenge is issued only after opaque commit,
 * and PRESENT completes only after the transparent transaction's committed callback and one final
 * exact-boundary recheck. AX WebView publication is deliberately outside this parser-blocking path.
 */
internal class ChromeMediaShieldActiveDocumentPresentationCoordinator(
    private val service: AccessibilityService,
    private val state: ChromePhotosProtectedSurfaceState,
    private val surface: ChromePhotosProtectedSurface,
    private val windowInspector: ChromeVisualWindowInspector,
    private val attestationReader: ChromePhotosDataPlaneAttestationReader,
    private val onLegacyWorkCancelled: () -> Unit,
    private val secureRandom: SecureRandom = SecureRandom(),
) : AutoCloseable,
    ChromeMediaShieldActiveDocumentLabControl.Endpoint {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val leaseAuthority = ChromePhotosDataPlaneLeaseAuthority()
    private val contextReader = ChromeMediaShieldActiveDocumentContextReader(windowInspector)
    private val parserBarrier =
        ChromeMediaShieldParserBarrierCoordinator(service, contextReader::readCurrent)
    private val replayProbe = ChromeMediaShieldActiveDocumentReplayProbe()
    private val helloAdmission =
        ChromeMediaShieldActiveDocumentHelloAdmission(
            readContext = contextReader::readCurrent,
            claimCurrent = { claim -> registryCurrent(claim) && attestationCurrent(claim) },
            onWaiting = { claim, reason ->
                protocolLogger.logRejected("active_hello_waiting_foreground", claim, reason)
            },
            onAccepted = ::activateHello,
            onRejected = { claim, reason ->
                if (reason == "hello_claim_stale" || reason == "hello_superseded") {
                    backgroundRejected = backgroundRejected.incremented()
                } else {
                    staleRejected = staleRejected.incremented()
                }
                protocolLogger.logRejected("active_hello_rejected", claim, reason)
            },
        )
    private val hold = ChromeMediaShieldActiveDocumentHold()
    private val revocation =
        ChromeMediaShieldActiveDocumentRevocationCoordinator(
            surfaceAlreadyOpaque = {
                val stats = surface.stats()
                !stats.transparent && stats.alphaTransitionsOutstanding == 0
            },
            submitOpaque = surface::revokeTransparency,
        )
    private val registration =
        ChromeMediaShieldActiveDocumentHandshakeBridge.register(
            ChromeMediaShieldActiveDocumentHandshakeListener(::onHandshakeRequest),
        )
    private val leaseWatchdog = Runnable(::verifyLeaseOnMain)
    private var attemptSequence = 0L
    private var attempt: ChromeMediaShieldActiveDocumentAttempt? = null
    private var activeHello = 0L
    private var challengeIssued = 0L
    private var proofAccepted = 0L
    private var presentAccepted = 0L
    private var backgroundRejected = 0L
    private var staleRejected = 0L
    private var crossTabRelease = 0L
    private var rejectedTransparentCommits = 0L
    private var opaqueRestoreFailures = 0L
    private var activeCaseId = ""
    private var closed = false
    private val protocolLogger by lazy(LazyThreadSafetyMode.NONE) {
        ChromeMediaShieldActiveDocumentProtocolLogger(
            contextCurrent = { current ->
                when {
                    attempt !== current -> false
                    current.surface == null -> isClaimAndBindingCurrent(current)
                    else -> isCurrent(current)
                }
            },
            surfaceEpoch = { state.snapshot().epoch },
            metrics = ::metrics,
            caseId = ::caseId,
            isClosed = { closed },
        )
    }

    init {
        ChromeMediaShieldActiveDocumentLabControl.bind(this)
    }

    fun hasCurrentClaim(): Boolean =
        !closed && (helloAdmission.hasCurrentClaim() || attempt?.let { registryCurrent(it.claim) } == true)

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        checkMainThread()
        parserBarrier.onAccessibilityEvent(event)
        if (event.packageName?.toString() == ActiveDocumentChromePackageName) helloAdmission.onChromeStructuralEvent()
        val current = attempt ?: return
        if (event.packageName?.toString() != ActiveDocumentChromePackageName && event.windowId != current.binding.windowId) return
        if (contextReader.currentBinding(current.binding.windowId) != current.binding) {
            backgroundRejected = backgroundRejected.incremented()
            invalidateCurrent("invalidated_root")
        }
    }

    fun prepareCoveredSnapshot(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        @Suppress("UNUSED_PARAMETER") retainCurrentDocument: Boolean,
    ) {
        checkMainThread()
        val current = attempt ?: return
        if (current.surface != snapshot) invalidateCurrent("invalidated_surface")
    }

    fun onOpaqueCommitted(snapshot: ChromePhotosProtectedSurfaceSnapshot) {
        checkMainThread()
        val current = attempt ?: return
        if (current.stage != ChromeMediaShieldActiveDocumentAttemptStage.AwaitingOpaque || current.surface != snapshot) {
            return
        }
        if (!isCurrent(current)) {
            rejectAttempt(current, "hello_context_stale")
            return
        }
        val challenge = ChromeMediaShieldActiveDocumentChallengeFactory.random(secureRandom)
        current.challenge = challenge
        maybeHold(current, HoldChallengeIssued) {
            if (!isCurrent(current)) {
                rejectAttempt(current, "hello_context_stale")
                return@maybeHold
            }
            val completion = current.pendingCompletion ?: return@maybeHold
            current.pendingCompletion = null
            if (!completion.issueChallenge(challenge)) {
                rejectAttempt(current, "hello_context_stale")
                return@maybeHold
            }
            current.stage = ChromeMediaShieldActiveDocumentAttemptStage.Challenged
            challengeIssued = challengeIssued.incremented()
            protocolLogger.log("challenge_issued", current)
        }
    }

    fun onHostPublicationChanged(): Boolean {
        checkMainThread()
        val current = attempt ?: return false
        if (current.stage != ChromeMediaShieldActiveDocumentAttemptStage.AwaitingOpaque) return true
        submitOpaqueCover(current)
        return true
    }

    fun isAwaitingCurrentMarker(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        viewport: ChromeVisualViewport,
        windowId: Int,
    ): Boolean {
        checkMainThread()
        val current = attempt ?: return false
        return current.stage != ChromeMediaShieldActiveDocumentAttemptStage.Released &&
            current.surface == snapshot &&
            current.binding.windowId == windowId &&
            current.binding.viewport == viewport &&
            isCurrent(current)
    }

    fun hasVerifiedPresentation(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        viewport: ChromeVisualViewport,
        windowId: Int,
    ): Boolean {
        checkMainThread()
        val current = attempt ?: return false
        val lease = current.lease ?: return false
        if (
            current.stage != ChromeMediaShieldActiveDocumentAttemptStage.Released ||
            current.surface != snapshot ||
            current.binding.windowId != windowId ||
            current.binding.viewport != viewport ||
            !surface.stats().transparent
        ) {
            return false
        }
        val context = current.leaseContext()
        return isCurrent(current) && leaseAuthority.isValid(lease, attestationReader.read(), context)
    }

    fun revokePresentation(
        reason: String,
        @Suppress("UNUSED_PARAMETER") forgetClaim: Boolean = false,
    ): Boolean {
        checkMainThread()
        val ownedSurfaceAuthority = attempt != null || revocation.hasPending()
        invalidateCurrent(reason)
        return ownedSurfaceAuthority
    }

    override fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            service.mainExecutor.execute(::close)
            return
        }
        if (closed) return
        closed = true
        replayProbe.clear()
        ChromeMediaShieldActiveDocumentLabControl.unbind(this)
        parserBarrier.close()
        helloAdmission.close()
        contextReader.close()
        revocation.close()
        registration.close()
        invalidateCurrent("invalidated_stop")
    }

    override fun arm(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String {
        checkMainThread()
        val accepted =
            caseId != null && stage != null && nonce != null && hold.arm(caseId, stage, nonce)
        if (!accepted) return "result=active_document_hold_rejected"
        activeCaseId = checkNotNull(caseId)
        protocolLogger.logHold("active_document_hold_armed", checkNotNull(hold.snapshot()))
        return "result=active_document_hold_armed"
    }

    override fun release(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String {
        checkMainThread()
        if (caseId == null || stage == null || nonce == null) return "result=active_document_hold_rejected"
        val released = hold.release(caseId, stage, nonce) ?: return "result=active_document_hold_rejected"
        protocolLogger.logHold("active_document_hold_released", released)
        return "result=active_document_hold_released"
    }

    override fun cancel(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String {
        checkMainThread()
        if (caseId == null || stage == null || nonce == null) return "result=active_document_hold_rejected"
        val cancelled = hold.cancel(caseId, stage, nonce) ?: return "result=active_document_hold_rejected"
        protocolLogger.logHold("active_document_hold_cancelled", cancelled, "hold_cancelled")
        return "result=active_document_hold_cancelled"
    }

    override fun status(): String {
        checkMainThread()
        return ChromeMediaShieldActiveDocumentProtocolDiagnostics.status(
            metrics = metrics(),
            foreground = contextReader.currentBinding(),
            owned = attempt?.binding,
            pendingHandshake = ChromeMediaShieldActiveDocumentHandshakeBridge.snapshot().pendingRequests,
            holdPhase = hold.snapshot().phase.name.lowercase(),
        )
    }

    override fun replayConsumedPresent(): String {
        checkMainThread()
        val currentReleasedSequence =
            attempt
                ?.takeIf { it.stage == ChromeMediaShieldActiveDocumentAttemptStage.Released }
                ?.sequence
        val result =
            replayProbe.replay(currentReleasedSequence) { request, completion ->
                handleRequestOnMain(request, completion)
            }
        return result.protocolResult
    }

    private fun onHandshakeRequest(
        request: ChromeMediaShieldActiveDocumentRequest,
        completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    ) {
        val guardedCompletion = ChromeMediaShieldActiveDocumentGuardedCompletion(completion)
        val registration =
            completion.onTransportCancelled {
                guardedCompletion.cancelTransport()
                service.mainExecutor.execute { onTransportCancelledOnMain(guardedCompletion) }
            }
        if (registration != ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered) return
        service.mainExecutor.execute {
            if (!guardedCompletion.dispatchGuard.mayDispatch(registration)) return@execute
            handleRequestOnMain(request, guardedCompletion)
        }
    }

    private fun onTransportCancelledOnMain(completion: ChromeMediaShieldActiveDocumentGuardedCompletion) {
        checkMainThread()
        if (helloAdmission.onTransportCancelled(completion)) return
        if (revocation.onTransportCancelled(completion)) return
        val current = attempt?.takeIf { it.pendingCompletion === completion } ?: return
        rejectAttempt(current, "handshake_transport_cancelled")
    }

    private fun handleRequestOnMain(
        request: ChromeMediaShieldActiveDocumentRequest,
        completion: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ) {
        checkMainThread()
        if (closed) {
            completion.reject()
            return
        }
        when (request) {
            is ChromeMediaShieldActiveDocumentRequest.Hello -> acceptHello(request.claim, completion)
            is ChromeMediaShieldActiveDocumentRequest.Prove -> acceptProof(request, completion)
            is ChromeMediaShieldActiveDocumentRequest.Present -> acceptPresent(request, completion)
            is ChromeMediaShieldActiveDocumentRequest.Revoke -> acceptRevoke(request, completion)
        }
    }

    private fun acceptHello(
        claim: ChromeMediaShieldReadyClaim,
        completion: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ) {
        activeHello = activeHello.incremented()
        helloAdmission.accept(claim, completion)
    }

    private fun activateHello(
        claim: ChromeMediaShieldReadyClaim,
        binding: ChromeMediaShieldActiveDocumentNativeBinding,
        completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    ) {
        val guardedCompletion =
            completion as? ChromeMediaShieldActiveDocumentGuardedCompletion ?: run {
                completion.reject()
                return
            }
        if (!guardedCompletion.isTransportCurrent()) {
            guardedCompletion.reject()
            return
        }
        invalidateCurrent("invalidated_navigation")
        onLegacyWorkCancelled()
        attemptSequence += 1L
        val current = ChromeMediaShieldActiveDocumentAttempt(attemptSequence, claim, binding, guardedCompletion)
        attempt = current
        protocolLogger.log("active_hello_accepted", current)
        maybeHold(current, HoldHelloAccepted) {
            if (!isClaimAndBindingCurrent(current)) {
                rejectAttempt(current, "hello_context_stale")
            } else {
                beginOpaqueCover(current)
            }
        }
    }

    private fun beginOpaqueCover(current: ChromeMediaShieldActiveDocumentAttempt) {
        if (attempt !== current) return
        val previous = state.snapshot()
        current.surface =
            if (!previous.isActive) {
                state.arm(current.binding.windowId, current.binding.viewport)
            } else {
                state.invalidate(current.binding.windowId, current.binding.viewport, motion = false)
            }
        current.stage = ChromeMediaShieldActiveDocumentAttemptStage.AwaitingOpaque
        submitOpaqueCover(current)
    }

    private fun submitOpaqueCover(current: ChromeMediaShieldActiveDocumentAttempt) {
        val expectedSurface = current.surface ?: return
        when (
            surface.cover(
                current.binding.windowId,
                current.binding.viewport,
                expectedSurface.epoch,
            ) { committedEpoch ->
                if (committedEpoch == expectedSurface.epoch) onOpaqueCommitted(expectedSurface)
            }
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> rejectAttempt(current, "hello_surface_failed")
            ChromePhotosProtectedSurfaceCoverResult.Pending -> Unit
            ChromePhotosProtectedSurfaceCoverResult.Ready -> Unit
        }
    }

    private fun acceptProof(
        request: ChromeMediaShieldActiveDocumentRequest.Prove,
        completion: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ) {
        val current = attempt
        if (
            current == null ||
            !ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                current.claim,
                current.challenge,
                current.stage,
                request,
            ) ||
            !isCurrent(current)
        ) {
            staleRejected = staleRejected.incremented()
            completion.reject()
            val reason =
                ChromeMediaShieldActiveDocumentBoundaryPolicy.proofRejectionReason(
                    current?.claim,
                    current?.challenge,
                    current?.stage,
                    request,
                )
            protocolLogger.logRejected(
                "proof_rejected",
                request.claim,
                reason,
            )
            return
        }
        current.pendingCompletion = completion
        maybeHold(current, HoldProofAccepted) {
            val proofCompletion = current.pendingCompletion ?: return@maybeHold
            current.pendingCompletion = null
            if (!isCurrent(current)) {
                proofCompletion.reject()
                rejectAttempt(current, "prove_context_changed")
                return@maybeHold
            }
            if (!proofCompletion.acceptProof()) {
                rejectAttempt(current, "prove_context_changed")
                return@maybeHold
            }
            current.stage = ChromeMediaShieldActiveDocumentAttemptStage.Proved
            proofAccepted = proofAccepted.incremented()
            protocolLogger.log("proof_accepted", current)
        }
    }

    private fun acceptPresent(
        request: ChromeMediaShieldActiveDocumentRequest.Present,
        completion: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ) {
        val current = attempt
        if (
            current == null ||
            !ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                current.claim,
                current.challenge,
                current.stage,
                request,
            ) ||
            !isCurrent(current)
        ) {
            staleRejected = staleRejected.incremented()
            completion.reject()
            val reason =
                ChromeMediaShieldActiveDocumentBoundaryPolicy.presentRejectionReason(
                    current?.claim,
                    current?.challenge,
                    current?.stage,
                    request,
                )
            protocolLogger.logRejected(
                "present_rejected",
                request.claim,
                reason,
            )
            return
        }
        current.pendingPresentRequest = request
        current.pendingCompletion = completion
        maybeHold(current, ChromeMediaShieldActiveDocumentHold.PresentPrecommit) {
            if (!isCurrent(current)) {
                rejectAttempt(current, "present_context_changed")
            } else {
                submitTransparent(current)
            }
        }
    }

    private fun submitTransparent(current: ChromeMediaShieldActiveDocumentAttempt) {
        val expectedSurface = current.surface ?: return rejectAttempt(current, "present_surface_not_opaque")
        if (attempt !== current || current.stage != ChromeMediaShieldActiveDocumentAttemptStage.Proved) return
        val transport =
            current.pendingCompletion?.takeIf { it.isTransportCurrent() }
                ?: return rejectAttempt(current, "present_transport_cancelled")
        val authority =
            ChromeMediaShieldActiveDocumentAuthority(
                claim = current.claim,
                windowId = current.binding.windowId,
                nativeRootDigest = current.binding.nativeRootDigest,
            )
        val context = current.leaseContext(authority)
        val lease =
            leaseAuthority.mint(attestationReader.read(), context)
                ?: return rejectAttempt(current, "present_context_changed")
        current.lease = lease
        current.stage = ChromeMediaShieldActiveDocumentAttemptStage.Committing
        val submitted =
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfTopLevelReadyCurrent(current.claim) {
                surface.presentTransparent(
                    lease = lease,
                    recheckCurrent = {
                        attempt === current &&
                            current.stage == ChromeMediaShieldActiveDocumentAttemptStage.Committing &&
                            current.surface == expectedSurface &&
                            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                                current.pendingCompletion,
                                transport,
                            ) &&
                            isCurrent(current) &&
                            leaseAuthority.isValid(lease, attestationReader.read(), context)
                    },
                    onCommitted = { committed -> onTransparentCommitted(current, context, committed) },
                    onRejectedPlatformCommit = { outcome ->
                        rejectedTransparentCommits = rejectedTransparentCommits.incremented()
                        if (contextReader.currentBinding(current.binding.windowId) != current.binding) {
                            crossTabRelease = crossTabRelease.incremented()
                        }
                        Log.w(LogTag, "phase=transparent_commit_rejected outcome=$outcome")
                    },
                )
            }
        if (!submitted && attempt === current) rejectAttempt(current, "present_commit_failed")
    }

    private fun onTransparentCommitted(
        current: ChromeMediaShieldActiveDocumentAttempt,
        context: ChromePhotosDataPlaneLeaseContext,
        committed: Boolean,
    ) {
        checkMainThread()
        if (committed && attempt === current && current.pendingCompletion?.isTransportCurrent() == true) {
            maybeHold(current, ChromeMediaShieldActiveDocumentHold.PresentPostcommit) {
                completeTransparentCommit(current, context, committed = true)
            }
            return
        }
        completeTransparentCommit(current, context, committed)
    }

    private fun completeTransparentCommit(
        current: ChromeMediaShieldActiveDocumentAttempt,
        context: ChromePhotosDataPlaneLeaseContext,
        committed: Boolean,
    ) {
        val completion = current.pendingCompletion
        current.pendingCompletion = null
        val stillCurrent =
            committed &&
                attempt === current &&
                completion?.isTransportCurrent() == true &&
                isCurrent(current)
        if (attempt !== current) {
            completion?.reject()
            return
        }
        if (!stillCurrent || current.lease?.let { leaseAuthority.isValid(it, attestationReader.read(), context) } != true) {
            completion?.reject()
            rejectAttempt(current, "present_postcommit_context_changed")
            return
        }
        current.stage = ChromeMediaShieldActiveDocumentAttemptStage.Released
        if (!completion.acceptPresentation()) {
            rejectAttempt(current, "present_commit_failed")
            return
        }
        val consumedPresent = current.pendingPresentRequest
        current.pendingPresentRequest = null
        if (consumedPresent == null) {
            rejectAttempt(current, "present_commit_failed")
            return
        }
        replayProbe.rememberConsumedPresent(current.sequence, consumedPresent)
        presentAccepted = presentAccepted.incremented()
        protocolLogger.log("present_accepted", current)
        protocolLogger.log("active_document_released", current)
        scheduleLeaseWatchdog()
    }

    private fun acceptRevoke(
        request: ChromeMediaShieldActiveDocumentRequest.Revoke,
        completion: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ) {
        val current = attempt
        if (
            current == null ||
            !ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                current.claim,
                current.challenge,
                current.stage,
                request,
            )
        ) {
            staleRejected = staleRejected.incremented()
            completion.reject()
            return
        }
        attempt = null
        replayProbe.clear(current.sequence)
        mainHandler.removeCallbacks(leaseWatchdog)
        hold.cancel()
        leaseAuthority.revoke()
        current.pendingCompletion?.reject()
        current.pendingCompletion = null
        current.pendingPresentRequest = null
        revocation.begin(current.sequence, completion) { terminal ->
            if (
                terminal.result.decision == ChromeMediaShieldActiveDocumentRevocationDecision.Accepted &&
                terminal.transportCompleted
            ) {
                protocolLogger.log("active_document_revoked", current)
            } else if (
                terminal.result.reason == ChromeMediaShieldActiveDocumentRevocationReason.SubmissionFailed
            ) {
                opaqueRestoreFailures = opaqueRestoreFailures.incremented()
                protocolLogger.log("active_document_revoke_rejected", current, "opaque_submission_failed")
            }
        }
    }

    private fun isCurrent(current: ChromeMediaShieldActiveDocumentAttempt): Boolean {
        val expectedSurface = current.surface ?: return false
        return ChromeMediaShieldActiveDocumentBoundaryPolicy.isExactBoundary(
            expected = current.binding,
            observed = contextReader.currentBinding(current.binding.windowId),
            expectedSurface = expectedSurface,
            currentSurface = state.snapshot(),
            claimCurrent = registryCurrent(current.claim),
            attestationCurrent = attestationCurrent(current.claim),
        )
    }

    private fun isClaimAndBindingCurrent(current: ChromeMediaShieldActiveDocumentAttempt): Boolean =
        attempt === current &&
            registryCurrent(current.claim) &&
            attestationCurrent(current.claim) &&
            contextReader.currentBinding(current.binding.windowId) == current.binding

    private fun registryCurrent(claim: ChromeMediaShieldReadyClaim): Boolean =
        ChromeMediaShieldDocumentAuthorityRegistry.commitIfTopLevelReadyCurrent(claim) { true }

    private fun attestationCurrent(claim: ChromeMediaShieldReadyClaim): Boolean =
        ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
            claim,
            attestationReader.read(),
            SystemClock.elapsedRealtime(),
        )

    private fun maybeHold(
        current: ChromeMediaShieldActiveDocumentAttempt,
        stage: String,
        continuation: () -> Unit,
    ) {
        val previousStage = current.stage
        current.stage = ChromeMediaShieldActiveDocumentAttemptStage.Held
        val held =
            hold.reach(stage) { proceed ->
                mainHandler.removeCallbacksAndMessages(current.holdTimeoutToken)
                if (attempt !== current || current.stage != ChromeMediaShieldActiveDocumentAttemptStage.Held) return@reach
                current.stage = previousStage
                if (proceed) {
                    continuation()
                } else {
                    rejectAttempt(current, "hold_cancelled")
                }
            }
        if (held == null) {
            current.stage = previousStage
            continuation()
            return
        }
        protocolLogger.logHold("active_document_hold_reached", held)
        mainHandler.postAtTime(
            {
                val snapshot = hold.snapshot()
                if (snapshot.phase == ChromeMediaShieldActiveDocumentHoldPhase.Reached && snapshot.sequence == held.sequence) {
                    hold.cancel()
                    protocolLogger.logHold("active_document_hold_cancelled", held, "hold_timeout")
                }
            },
            current.holdTimeoutToken,
            SystemClock.uptimeMillis() + HoldTimeoutMillis,
        )
    }

    private fun verifyLeaseOnMain() {
        val current = attempt?.takeIf { it.stage == ChromeMediaShieldActiveDocumentAttemptStage.Released } ?: return
        val lease = current.lease ?: return
        val context = current.leaseContext()
        if (!isCurrent(current) || !leaseAuthority.isValid(lease, attestationReader.read(), context)) {
            invalidateCurrent("invalidated_health")
            return
        }
        if (lease.validUntilElapsed - SystemClock.elapsedRealtime() <= LeaseRenewalLeadMillis) {
            current.lease = leaseAuthority.mint(attestationReader.read(), context)
            if (current.lease == null) {
                invalidateCurrent("invalidated_health")
                return
            }
        }
        scheduleLeaseWatchdog()
    }

    private fun scheduleLeaseWatchdog() {
        mainHandler.removeCallbacks(leaseWatchdog)
        mainHandler.postDelayed(leaseWatchdog, LeaseWatchdogMillis)
    }

    private fun rejectAttempt(
        current: ChromeMediaShieldActiveDocumentAttempt,
        reason: String,
    ) {
        if (attempt !== current) {
            current.pendingCompletion?.reject()
            current.pendingCompletion = null
            current.pendingPresentRequest = null
            return
        }
        attempt = null
        replayProbe.clear(current.sequence)
        mainHandler.removeCallbacksAndMessages(current.holdTimeoutToken)
        if (reason == "invalidated_navigation") {
            hold.transferToSupersedingAttempt()
        } else {
            hold.cancel()
        }
        mainHandler.removeCallbacks(leaseWatchdog)
        current.pendingCompletion?.reject()
        current.pendingCompletion = null
        current.pendingPresentRequest = null
        leaseAuthority.revoke()
        revokeSurfaceTransparency()
        staleRejected = staleRejected.incremented()
        protocolLogger.log("active_document_invalidated", current, reason)
    }

    private fun invalidateCurrent(reason: String) {
        replayProbe.clear()
        helloAdmission.cancel(reason)
        revocation.cancelCurrent()
        val current = attempt
        if (current != null) {
            rejectAttempt(current, reason)
            return
        }
        mainHandler.removeCallbacks(leaseWatchdog)
        hold.cancel()
        leaseAuthority.revoke()
        revokeSurfaceTransparency()
    }

    private fun revokeSurfaceTransparency() {
        if (!surface.stats().transparent) return
        surface.revokeTransparency { committed ->
            if (!committed) opaqueRestoreFailures = opaqueRestoreFailures.incremented()
        }
    }

    private fun metrics(
        @Suppress("UNUSED_PARAMETER") phase: String = "",
    ): String =
        "activeHello=$activeHello challengeIssued=$challengeIssued " +
            "proofAccepted=$proofAccepted presentAccepted=$presentAccepted " +
            "backgroundRejected=$backgroundRejected staleRejected=$staleRejected " +
            "staleReplayRejected=${replayProbe.rejectedReplayCount()} " +
            "replayCandidate=${if (replayProbe.hasCandidate()) 1 else 0} " +
            "crossTabRelease=$crossTabRelease releaseCurrent=${if (attempt?.stage == ChromeMediaShieldActiveDocumentAttemptStage.Released) 1 else 0} " +
            "rejectedTransparentCommits=$rejectedTransparentCommits opaqueRestoreFailures=$opaqueRestoreFailures " +
            "alphaTransitionsOutstanding=${surface.stats().alphaTransitionsOutstanding} " +
            "alphaSubmitFailures=${surface.stats().alphaSubmitFailures}"

    private fun caseId(): String = activeCaseId.takeIf(String::isNotBlank) ?: "cold_foreground_release"

    private fun Long.incremented(): Long = if (this == Long.MAX_VALUE) this else this + 1L

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}
