package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.feature.accessibility.R
import com.glosh.visual.GloshiaVisualModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DEV-only R1 gate. It protects Chrome first, captures underneath that protection, keeps only a
 * bounded crop, and allows release only after a current GloshIA SAFE decision.
 */
internal class ChromeVisualShieldController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val onLabOwnershipChanged: (Boolean) -> Unit = {},
) : AutoCloseable,
    ChromeVisualShieldLabControl.Endpoint {
    private val enabled =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            service.packageName.endsWith(".dev") &&
            service.resources.getBoolean(R.bool.chrome_visual_shield_lab_enabled)
    private val metrics = ChromeVisualShieldMetrics()
    private val r1Metrics = ChromeVisualShieldR1Metrics()
    private val identityGate = ChromeVisualShieldIdentityGate(metrics::onStaleDropped)
    private val capture = ChromeWindowCapture(service, ChromeVisualShieldCaptureObserver(metrics))
    private val frameProcessor = ChromeVisualShieldFrameProcessor(metrics)
    private val analyzerFault = ChromeVisualShieldAnalyzerFault()
    private val gloshiaAnalyzer = ChromeVisualShieldGloshiaAnalyzer(service, analyzerFault)
    private val windowInspector = ChromeVisualWindowInspector(service)
    private val surface = ChromePhotosProtectedSurface(service, ::onHostPublicationChanged)
    private val regionContract =
        ChromeVisualShieldRegionContract(
            id = ChromeVisualShieldLabControl.RegionId,
            leftBasisPoints = ChromeVisualShieldLabControl.RegionLeftBasisPoints,
            topBasisPoints = ChromeVisualShieldLabControl.RegionTopBasisPoints,
            rightBasisPoints = ChromeVisualShieldLabControl.RegionRightBasisPoints,
            bottomBasisPoints = ChromeVisualShieldLabControl.RegionBottomBasisPoints,
            fixtureSignature = ChromeVisualShieldLabControl.FixtureSignature,
        )
    private val decisionAuthority =
        ChromeVisualShieldDecisionAuthority(
            identityGate = identityGate,
            metrics = r1Metrics,
            releaseSurface = ::releaseSafeSurface,
        )
    private val workProcessor =
        ChromeVisualShieldWorkProcessor(
            capture = capture,
            frameProcessor = frameProcessor,
            analyzer = gloshiaAnalyzer,
            identityGate = identityGate,
            metrics = r1Metrics,
            deliverDecision = ::deliverDecision,
            log = ::log,
            onCycleEnded = { logMetrics("cycle_end") },
        )
    private val workCoordinator =
        ChromeVisualShieldWorkCoordinator<ChromeVisualShieldWork>(
            scope = scope,
            onWorkSuperseded = r1Metrics::onWorkSuperseded,
            onActiveWorkCancelled = metrics::onCaptureCancelled,
            execute = workProcessor::execute,
        )
    private var pendingCoverEpoch: Long? = null
    private var sentinelCropMatches = 0L
    private var captureCycles = 0L
    private var opaqueCommittedCount = 0L
    private var labOwnershipPublished = false
    private var labActive = false
    private val closed = AtomicBoolean(false)

    init {
        if (enabled) ChromeVisualShieldLabControl.bind(this)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || !labActive || closed.get()) return
        runOnMain {
            r1Metrics.onEventReceived()
            val packageName = event.packageName?.toString().orEmpty()
            if (!windowInspector.isChromePackage(packageName)) {
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.Suspension)
                return@runOnMain
            }
            val current = identityGate.snapshot().context ?: return@runOnMain
            val window =
                windowInspector.find(event.windowId.takeIf { it >= 0 } ?: current.windowId)
                    ?: windowInspector.find(AnyWindowId)
            if (window == null) {
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.WindowReplaced)
                return@runOnMain
            }
            val viewport = windowInspector.viewport(window)
            if (viewport == null) {
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.Viewport)
                return@runOnMain
            }
            invalidateProtectCapture(window.id, viewport, reasonFor(event, current, window.id, viewport))
        }
    }

    fun ownsLabSession(): Boolean = enabled && labActive

    fun onAccessibilityUnavailable() {
        if (!labActive) return
        runOnMain {
            cancelWorkAndJoin()
            identityGate.failClosed(null)
            log("phase=accessibility_unavailable state=protected result=fail_close")
        }
    }

    override fun start(): String =
        commandOnMain {
            if (!enabled) return@commandOnMain "result=disabled"
            val window = windowInspector.find(AnyWindowId) ?: return@commandOnMain "result=chrome_absent"
            val viewport = windowInspector.viewport(window) ?: return@commandOnMain "result=viewport_absent"
            cancelWorkAndJoin()
            labActive = true
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(true)
            val started =
                identityGate.start(window.id, viewport, regionContract)
                    ?: return@commandOnMain "result=invalid_fixture_contract"
            if (!protectThenCapture(started, "start")) {
                labActive = false
                identityGate.stop()
                ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
                return@commandOnMain "result=surface_failed ${statusValue()}"
            }
            "result=started ${statusValue()}"
        }

    override fun stop(): String =
        commandOnMain {
            deactivate("explicit_stop")
            "result=stopped ${statusValue()}"
        }

    override fun release(): String =
        commandOnMain {
            val context = identityGate.snapshot().context ?: return@commandOnMain "result=no_context"
            if (!identityGate.releaseForExplicitLabGate(context)) {
                r1Metrics.onReleaseRejected()
                return@commandOnMain "result=release_rejected ${statusValue()}"
            }
            cancelWorkAndJoin()
            surface.close()
            labActive = false
            publishLabOwnership(false)
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
            log("phase=lab_release result=explicit_only rawPresented=false")
            "result=released ${statusValue()}"
        }

    override fun injectStale(): String =
        commandOnMain {
            val current = identityGate.snapshot().context ?: return@commandOnMain "result=no_context"
            cancelWorkAndJoin()
            val stale = identityGate.beginCapture() ?: return@commandOnMain "result=capture_not_ready"
            if (identityGate.beginProcessing(stale) is ChromeVisualShieldResult.Stale) {
                return@commandOnMain "result=processing_not_ready"
            }
            invalidateProtectCapture(
                windowId = current.windowId,
                viewport = current.viewport,
                reason = ChromeVisualShieldInvalidation.Navigation,
            )
            val result =
                decisionAuthority.apply(
                    expectedCycleIdentity = stale,
                    decision =
                        ChromeVisualShieldGloshiaDecision.Safe(
                            identity = stale,
                            reason = com.glosh.visual.GloshiaVisualPolicyContract.ModelAllowReason,
                            filterProbability = 0f,
                        ),
                )
            log("phase=stale_injection result=${result.logValue()} rawPresented=false")
            "result=${result.logValue()} ${statusValue()}"
        }

    override fun cancelStress(): String =
        commandOnMain {
            val current = identityGate.snapshot().context ?: return@commandOnMain "result=no_context"
            invalidateProtectCapture(
                current.windowId,
                current.viewport,
                ChromeVisualShieldInvalidation.Scroll,
            )
            cancelWorkAndJoin()
            identityGate.failClosed(null)
            log("phase=cancel_stress result=protected rawPresented=false")
            "result=cancelled ${statusValue()}"
        }

    override fun status(): String = commandOnMain(::statusValue)

    override fun armAnalyzerFailure(): String =
        commandOnMain {
            val result = if (analyzerFault.armOnce()) "armed" else "already_armed"
            "result=$result ${statusValue()}"
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ChromeVisualShieldLabControl.unbind(this)
        runOnMain {
            deactivate("service_closed")
            runBlocking { workCoordinator.shutdown() }
            gloshiaAnalyzer.close()
        }
    }

    private fun invalidateProtectCapture(
        windowId: Int,
        viewport: ChromeVisualViewport,
        reason: ChromeVisualShieldInvalidation,
    ) {
        if (!labActive) return
        r1Metrics.onContentInvalidation()
        val protected = identityGate.invalidate(windowId, viewport, regionContract, reason) ?: return
        workCoordinator.invalidateAuthority()
        protectThenCapture(protected, reason.name.lowercase())
    }

    private fun invalidateWithoutNewWindow(reason: ChromeVisualShieldInvalidation) {
        val current = identityGate.snapshot().context ?: return
        r1Metrics.onContentInvalidation()
        val protected =
            identityGate.invalidate(current.windowId, current.viewport, regionContract, reason) ?: return
        workCoordinator.invalidateAuthority()
        val context = protected.context ?: return
        surface.cover(context.windowId, context.viewport, context.contentEpoch)
        pendingCoverEpoch = null
        log("phase=protect_only trigger=${reason.name.lowercase()} result=fail_close")
    }

    private fun protectThenCapture(
        protected: ChromeVisualShieldStateSnapshot,
        trigger: String,
    ): Boolean {
        val context = protected.context ?: return false
        val epoch = context.contentEpoch
        pendingCoverEpoch = epoch
        when (
            surface.cover(context.windowId, context.viewport, epoch) { committedEpoch ->
                onOpaqueCommitted(committedEpoch, trigger)
            }
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> {
                pendingCoverEpoch = null
                identityGate.failClosed(null)
                log("phase=protect trigger=$trigger result=surface_failed")
                return false
            }
            ChromePhotosProtectedSurfaceCoverResult.Pending ->
                log("phase=protect trigger=$trigger result=host_pending")
            ChromePhotosProtectedSurfaceCoverResult.Ready ->
                log("phase=protect trigger=$trigger result=opaque_requested")
        }
        return true
    }

    private fun onHostPublicationChanged() {
        runOnMain {
            val context = identityGate.snapshot().context ?: return@runOnMain
            val epoch = pendingCoverEpoch ?: return@runOnMain
            if (context.contentEpoch != epoch) return@runOnMain
            protectThenCapture(identityGate.snapshot(), "host_ready")
        }
    }

    private fun onOpaqueCommitted(
        committedEpoch: Long,
        trigger: String,
    ) {
        runOnMain {
            val current = identityGate.snapshot().context ?: return@runOnMain
            if (!labActive || current.contentEpoch != committedEpoch) return@runOnMain
            pendingCoverEpoch = null
            opaqueCommittedCount += 1
            publishLabOwnership(true)
            scheduleCapture(trigger)
        }
    }

    private fun scheduleCapture(trigger: String) {
        val identity = identityGate.beginCapture() ?: return
        if (!workCoordinator.request(ChromeVisualShieldWork(identity, trigger))) {
            identityGate.failClosed(identity)
        }
    }

    private fun onCurrentDecision(sentinelMatches: Boolean) {
        if (sentinelMatches) sentinelCropMatches += 1
        captureCycles += 1
    }

    private fun deliverDecision(delivery: ChromeVisualShieldDecisionDelivery) {
        runOnMain {
            val result = decisionAuthority.apply(delivery.work.identity, delivery.decision)
            if (
                result != ChromeVisualShieldDecisionResult.StaleDropped &&
                result != ChromeVisualShieldDecisionResult.IdentityMismatchRejected
            ) {
                onCurrentDecision(delivery.sentinelMatches)
            }
            log(
                "phase=process trigger=${delivery.work.trigger} " +
                    "sentinelMatched=${delivery.sentinelMatches} " +
                    "decision=${delivery.decision.logValue()} reason=${delivery.decision.reason} " +
                    "result=${result.logValue()} rawPresented=false",
            )
        }
    }

    private fun releaseSafeSurface() {
        surface.close()
        pendingCoverEpoch = null
        publishLabOwnership(false)
    }

    private fun cancelWorkAndJoin() {
        runBlocking { workCoordinator.cancelAndJoin() }
    }

    private fun deactivate(reason: String) {
        cancelWorkAndJoin()
        identityGate.stop()
        surface.close()
        pendingCoverEpoch = null
        labActive = false
        publishLabOwnership(false)
        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
        log("phase=inactive reason=$reason rawPresented=false")
        logMetrics("deactivate")
    }

    private fun statusValue(): String {
        val state = identityGate.snapshot()
        val value = metrics.snapshot()
        val r1 = r1Metrics.snapshot()
        return "active=$labActive phase=${state.phase} session=${state.context?.protectionSessionId ?: 0} " +
            "windowId=${state.context?.windowId ?: -1} contentEpoch=${state.context?.contentEpoch ?: 0} " +
            "viewportEpoch=${state.context?.viewportEpoch ?: 0} regionSequence=${state.context?.regionSequence ?: 0} " +
            "fullFrameAcquired=${value.fullFrameAcquired} fullFrameClosed=${value.fullFrameClosed} " +
            "fullFrameOutstanding=${value.fullFrameOutstanding} fullFramePeakBytes=${value.fullFramePeakBytes} " +
            "cropCreated=${value.cropCreated} cropClosed=${value.cropClosed} cropOutstanding=${value.cropOutstanding} " +
            "staleDropped=${value.staleDropped} captureCancelled=${value.captureCancelled} " +
            "secureWindowFailures=${value.secureWindowFailures} captureCycles=$captureCycles " +
            "opaqueCommitted=$opaqueCommittedCount sentinelCropMatches=$sentinelCropMatches " +
            "labReleaseCount=${state.labReleaseCount} staleReleaseRejected=${state.staleReleaseRejected} " +
            "eventsReceived=${r1.eventsReceived} contentInvalidations=${r1.contentInvalidations} " +
            "workSuperseded=${r1.workSuperseded} inferenceStarted=${r1.inferenceStarted} " +
            "inferenceCompleted=${r1.inferenceCompleted} inferenceOutstanding=${r1.inferenceOutstanding} " +
            "inferencePeakOutstanding=${r1.inferencePeakOutstanding} " +
            "safeCurrent=${r1.safeCurrent} blockCurrent=${r1.blockCurrent} " +
            "failClosedCurrent=${r1.failClosedCurrent} staleInferenceDropped=${r1.staleInferenceDropped} " +
            "inferenceCancelled=${r1.inferenceCancelled} identityMismatchRejected=${r1.identityMismatchRejected} " +
            "releaseCurrent=${r1.releaseCurrent} releaseRejected=${r1.releaseRejected} " +
            "safeDecisionAtNanos=${r1.safeDecisionAtNanos} releaseAtNanos=${r1.releaseAtNanos} " +
            "workIdle=${workCoordinator.isIdle()} model=${GloshiaVisualModelInfo.FunctionalVersion} " +
            "modelSha=${GloshiaVisualModelInfo.ModelSha256} rawPersisted=0 rawUploaded=0"
    }

    private fun logMetrics(reason: String) {
        log("phase=metrics reason=$reason ${statusValue()}")
    }

    private fun publishLabOwnership(active: Boolean) {
        if (labOwnershipPublished == active) return
        labOwnershipPublished = active
        onLabOwnershipChanged(active)
    }

    private fun reasonFor(
        event: AccessibilityEvent,
        current: ChromeVisualShieldContext,
        windowId: Int,
        viewport: ChromeVisualViewport,
    ): ChromeVisualShieldInvalidation =
        when {
            windowId != current.windowId -> ChromeVisualShieldInvalidation.WindowReplaced
            viewport != current.viewport -> ChromeVisualShieldInvalidation.Viewport
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                ChromeVisualShieldInvalidation.Scroll
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                ChromeVisualShieldInvalidation.WindowReplaced
            else -> ChromeVisualShieldInvalidation.Navigation
        }

    private fun commandOnMain(block: () -> String): String =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            "result=wrong_thread"
        }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else service.mainExecutor.execute(block)
    }

    private fun ChromeVisualShieldDecisionResult.logValue(): String =
        when (this) {
            ChromeVisualShieldDecisionResult.SafeReleased -> "safe_released"
            ChromeVisualShieldDecisionResult.BlockProtected -> "block_protected"
            ChromeVisualShieldDecisionResult.FailClosed -> "fail_closed"
            ChromeVisualShieldDecisionResult.StaleDropped -> "stale_drop"
            ChromeVisualShieldDecisionResult.IdentityMismatchRejected -> "identity_mismatch_rejected"
            ChromeVisualShieldDecisionResult.ReleaseRejected -> "release_rejected"
        }

    private fun ChromeVisualShieldGloshiaDecision.logValue(): String =
        when (this) {
            is ChromeVisualShieldGloshiaDecision.Safe -> "safe"
            is ChromeVisualShieldGloshiaDecision.Block -> "block"
            is ChromeVisualShieldGloshiaDecision.FailClosed -> "fail_closed"
        }

    private fun log(message: String) {
        Log.i(LogTag, "$message gloshIAConnected=true")
    }

    private companion object {
        const val AnyWindowId = -1
        const val LogTag = "GloshVisualShield"
    }
}
