package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.feature.accessibility.R
import com.glosh.visual.GloshiaVisualModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val gloshiaAnalyzer = ChromeVisualShieldGloshiaAnalyzer(service)
    private val eventCoalescer = ChromeVisualShieldEventCoalescer()
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
    private var activeJob: Job? = null
    private var pendingCoverEpoch: Long? = null
    private var lastCaptureIdentity: ChromeVisualShieldIdentity? = null
    private var sentinelCropMatches = 0L
    private var captureCycles = 0L
    private var opaqueCommittedCount = 0L
    private var labOwnershipPublished = false
    private var labActive = false
    private val closed = AtomicBoolean(false)
    private val jobLock = Any()

    init {
        if (enabled) ChromeVisualShieldLabControl.bind(this)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || !labActive || closed.get()) return
        runOnMain {
            r1Metrics.onEventReceived()
            val packageName = event.packageName?.toString().orEmpty()
            if (!windowInspector.isChromePackage(packageName)) {
                eventCoalescer.reset()
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.Suspension)
                return@runOnMain
            }
            val current = identityGate.snapshot().context ?: return@runOnMain
            val window =
                windowInspector.find(event.windowId.takeIf { it >= 0 } ?: current.windowId)
                    ?: windowInspector.find(AnyWindowId)
            if (window == null) {
                eventCoalescer.reset()
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.WindowReplaced)
                return@runOnMain
            }
            val viewport = windowInspector.viewport(window)
            if (viewport == null) {
                eventCoalescer.reset()
                invalidateWithoutNewWindow(ChromeVisualShieldInvalidation.Viewport)
                return@runOnMain
            }
            val state = identityGate.snapshot()
            val fingerprint =
                ChromeVisualShieldEventFingerprint(
                    eventType = event.eventType,
                    eventTime = event.eventTime,
                    contentChangeTypes = event.contentChangeTypes,
                    windowId = window.id,
                    viewport = viewport,
                )
            if (
                eventCoalescer.shouldCoalesce(
                    fingerprint = fingerprint,
                    phase = state.phase,
                    eligible = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            ) {
                r1Metrics.onEventCoalesced()
                log("phase=event result=coalesced type=${event.eventType}")
                return@runOnMain
            }
            invalidateProtectCapture(window.id, viewport, reasonFor(event, current, window.id, viewport))
        }
    }

    fun ownsLabSession(): Boolean = enabled && labActive

    fun onAccessibilityUnavailable() {
        if (!labActive) return
        runOnMain {
            cancelCapture()
            identityGate.failClosed(null)
            log("phase=accessibility_unavailable state=protected result=fail_close")
        }
    }

    override fun start(): String =
        commandOnMain {
            if (!enabled) return@commandOnMain "result=disabled"
            val window = windowInspector.find(AnyWindowId) ?: return@commandOnMain "result=chrome_absent"
            val viewport = windowInspector.viewport(window) ?: return@commandOnMain "result=viewport_absent"
            cancelCapture()
            eventCoalescer.reset()
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
            cancelCapture()
            surface.close()
            labActive = false
            publishLabOwnership(false)
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
            log("phase=lab_release result=explicit_only rawPresented=false")
            "result=released ${statusValue()}"
        }

    override fun injectStale(): String =
        commandOnMain {
            val stale = lastCaptureIdentity ?: return@commandOnMain "result=no_capture_identity"
            val current = identityGate.snapshot().context ?: return@commandOnMain "result=no_context"
            invalidateProtectCapture(
                windowId = current.windowId,
                viewport = current.viewport,
                reason = ChromeVisualShieldInvalidation.Navigation,
            )
            val result = identityGate.completeProcessing(stale)
            if (result is ChromeVisualShieldResult.Stale) r1Metrics.onStaleInferenceDropped()
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
            cancelCapture()
            identityGate.failClosed(null)
            log("phase=cancel_stress result=protected rawPresented=false")
            "result=cancelled ${statusValue()}"
        }

    override fun status(): String = commandOnMain(::statusValue)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ChromeVisualShieldLabControl.unbind(this)
        runOnMain {
            deactivate("service_closed")
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
        cancelCapture()
        val protected = identityGate.invalidate(windowId, viewport, regionContract, reason) ?: return
        protectThenCapture(protected, reason.name.lowercase())
    }

    private fun invalidateWithoutNewWindow(reason: ChromeVisualShieldInvalidation) {
        val current = identityGate.snapshot().context ?: return
        r1Metrics.onContentInvalidation()
        cancelCapture()
        val protected =
            identityGate.invalidate(current.windowId, current.viewport, regionContract, reason) ?: return
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
        var scheduled: Job? = null
        scheduled =
            scope.launch {
                try {
                    captureAndProcess(identity, trigger)
                } catch (cancelled: CancellationException) {
                    withContext(Dispatchers.Main.immediate) { identityGate.failClosed(identity) }
                    throw cancelled
                } finally {
                    synchronized(jobLock) {
                        if (activeJob === scheduled) activeJob = null
                    }
                    logMetrics("cycle_end")
                }
            }
        synchronized(jobLock) { activeJob = scheduled }
    }

    private suspend fun captureAndProcess(
        identity: ChromeVisualShieldIdentity,
        trigger: String,
    ) {
        when (val result = capture.capture(identity.windowId)) {
            is ChromeWindowCaptureResult.Failed -> {
                withContext(Dispatchers.Main.immediate) { identityGate.failClosed(identity) }
                log("phase=capture trigger=$trigger errorCode=${result.errorCode} result=fail_close")
            }
            is ChromeWindowCaptureResult.Captured -> {
                ChromeVisualShieldCaptureResources<ChromeWindowFrame, ChromeVisualShieldCrop>()
                    .use { resources ->
                        resources.attachFullFrame(result.frame)
                        val crop = resources.deriveCrop { frameProcessor.crop(it, identity) }
                        if (crop == null) {
                            withContext(Dispatchers.Main.immediate) { identityGate.failClosed(identity) }
                            log("phase=crop trigger=$trigger result=fail_close")
                            return
                        }
                        resources.processCrop { ownedCrop ->
                            val processing =
                                withContext(Dispatchers.Main.immediate) {
                                    identityGate.beginProcessing(identity)
                                }
                            if (processing is ChromeVisualShieldResult.Stale) {
                                r1Metrics.onStaleInferenceDropped()
                                return@processCrop
                            }
                            val matches = sentinelMatches(ownedCrop.bitmap)
                            r1Metrics.onInferenceStarted()
                            val decision =
                                try {
                                    gloshiaAnalyzer.analyze(
                                        bitmap = ownedCrop.bitmap,
                                        identity = identity,
                                        canContinue = { isCurrentProcessingIdentity(identity) },
                                    )
                                } catch (cancelled: CancellationException) {
                                    r1Metrics.onInferenceCancelled()
                                    throw cancelled
                                } finally {
                                    r1Metrics.onInferenceCompleted()
                                }
                            val resultLabel =
                                withContext(Dispatchers.Main.immediate) {
                                    applyGloshiaDecision(identity, decision, matches)
                                }
                            log(
                                "phase=process trigger=$trigger sentinelMatched=$matches " +
                                    "decision=${decision.logValue()} reason=${decision.reason} " +
                                    "result=$resultLabel rawPresented=false",
                            )
                        }
                    }
            }
        }
    }

    private fun applyGloshiaDecision(
        identity: ChromeVisualShieldIdentity,
        decision: ChromeVisualShieldGloshiaDecision,
        sentinelMatches: Boolean,
    ): String {
        val completion = identityGate.completeProcessing(identity)
        if (completion is ChromeVisualShieldResult.Stale) {
            r1Metrics.onStaleInferenceDropped()
            return "stale_drop"
        }
        if (sentinelMatches) sentinelCropMatches += 1
        lastCaptureIdentity = identity
        captureCycles += 1
        return when (decision) {
            is ChromeVisualShieldGloshiaDecision.Safe -> {
                r1Metrics.onSafeCurrent()
                if (!identityGate.releaseForExplicitLabGate(identity.toContext())) {
                    r1Metrics.onReleaseRejected()
                    identityGate.failClosed(null)
                    "safe_release_rejected"
                } else {
                    r1Metrics.onReleaseCurrent()
                    surface.close()
                    pendingCoverEpoch = null
                    publishLabOwnership(false)
                    "safe_released"
                }
            }
            is ChromeVisualShieldGloshiaDecision.Block -> {
                r1Metrics.onBlockCurrent()
                "block_protected"
            }
            is ChromeVisualShieldGloshiaDecision.FailClosed -> {
                r1Metrics.onFailClosedCurrent()
                "fail_closed"
            }
        }
    }

    private fun isCurrentProcessingIdentity(identity: ChromeVisualShieldIdentity): Boolean {
        val state = identityGate.snapshot()
        val context = state.context ?: return false
        return state.phase == ChromeVisualShieldPhase.Processing &&
            state.nextCaptureSequence == identity.captureSequence + 1 &&
            context.protectionSessionId == identity.protectionSessionId &&
            context.windowId == identity.windowId &&
            context.contentEpoch == identity.contentEpoch &&
            context.viewport == identity.viewport &&
            context.viewportEpoch == identity.viewportEpoch &&
            context.regionId == identity.regionId &&
            context.regionSequence == identity.regionSequence &&
            context.region == identity.region
    }

    private fun ChromeVisualShieldIdentity.toContext() =
        ChromeVisualShieldContext(
            protectionSessionId = protectionSessionId,
            windowId = windowId,
            contentEpoch = contentEpoch,
            viewport = viewport,
            viewportEpoch = viewportEpoch,
            regionId = regionId,
            regionSequence = regionSequence,
            region = region,
        )

    private fun sentinelMatches(bitmap: android.graphics.Bitmap): Boolean {
        if (bitmap.width < 4 || bitmap.height < 2) return false
        val redCandidate = bitmap.getPixel(bitmap.width / 4, bitmap.height / 2)
        val blackCandidate = bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 2)
        return ChromeVisualShieldExposureProbe.isSentinelPair(
            ChromeVisualShieldRgb(
                Color.red(redCandidate),
                Color.green(redCandidate),
                Color.blue(redCandidate),
            ),
            ChromeVisualShieldRgb(
                Color.red(blackCandidate),
                Color.green(blackCandidate),
                Color.blue(blackCandidate),
            ),
        )
    }

    private fun cancelCapture() {
        val job =
            synchronized(jobLock) {
                activeJob.also { activeJob = null }
            }
        if (job != null && job.isActive) {
            metrics.onCaptureCancelled()
            job.cancel()
        }
    }

    private fun deactivate(reason: String) {
        cancelCapture()
        identityGate.stop()
        surface.close()
        eventCoalescer.reset()
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
            "eventsReceived=${r1.eventsReceived} eventsCoalesced=${r1.eventsCoalesced} " +
            "contentInvalidations=${r1.contentInvalidations} inferenceStarted=${r1.inferenceStarted} " +
            "inferenceCompleted=${r1.inferenceCompleted} inferenceOutstanding=${r1.inferenceOutstanding} " +
            "safeCurrent=${r1.safeCurrent} blockCurrent=${r1.blockCurrent} " +
            "failClosedCurrent=${r1.failClosedCurrent} staleInferenceDropped=${r1.staleInferenceDropped} " +
            "inferenceCancelled=${r1.inferenceCancelled} releaseCurrent=${r1.releaseCurrent} " +
            "releaseRejected=${r1.releaseRejected} model=${GloshiaVisualModelInfo.FunctionalVersion} " +
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

    private fun ChromeVisualShieldResult.logValue(): String =
        when (this) {
            ChromeVisualShieldResult.Current -> "current"
            ChromeVisualShieldResult.Stale -> "stale_drop"
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
