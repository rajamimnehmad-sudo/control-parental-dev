package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.feature.accessibility.R
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
        ChromeVisualShieldLabAvailability.isEnabled(
            sdkInt = Build.VERSION.SDK_INT,
            packageName = service.packageName,
            resourceEnabled = service.resources.getBoolean(R.bool.chrome_visual_shield_lab_enabled),
        )
    private val metrics = ChromeVisualShieldMetrics()
    private val r1Metrics = ChromeVisualShieldR1Metrics()
    private val identityGate = ChromeVisualShieldIdentityGate(metrics::onStaleDropped)
    private val viewportRenderGate = ChromeVisualShieldViewportRenderGate()
    private val capture = ChromeWindowCapture(service, ChromeVisualShieldCaptureObserver(metrics))
    private val frameProcessor = ChromeVisualShieldFrameProcessor(metrics)
    private val analyzerFault = ChromeVisualShieldAnalyzerFault()
    private val gloshiaAnalyzer = ChromeVisualShieldGloshiaAnalyzer(service, analyzerFault)
    private val regionAnalyzer = ChromeVisualRegionAnalyzer(service)
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
    private val renderProbeAuthority = ChromeVisualShieldRenderProbeAuthority(identityGate, r1Metrics)
    private val regionDiscoveryAuthority = ChromeVisualShieldRegionDiscoveryAuthority(identityGate, r1Metrics)
    private val regionDiscoveryLab = ChromeVisualShieldRegionDiscoveryLab(regionDiscoveryAuthority)
    private val rasterProvenanceObserver = ChromeVisualShieldRasterProvenanceObserver(::log)
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
            execute = ::executeWork,
        )
    private val regionDiscoveryProcessor =
        ChromeVisualShieldRegionDiscoveryWorkProcessor(
            capture = capture,
            frameProcessor = frameProcessor,
            planner = ChromeVisualShieldRegionDiscoveryPlanner(),
            analyzer = regionAnalyzer,
            identityGate = identityGate,
            metrics = r1Metrics,
            rasterProvenanceObserver = rasterProvenanceObserver,
            deliver = ::deliverRegionDiscovery,
            log = ::log,
            onCycleEnded = { logMetrics("region_discovery_cycle_end") },
        )
    private var pendingCoverEpoch: Long? = null
    private var sentinelCropMatches = 0L
    private var captureCycles = 0L
    private var opaqueCommittedCount = 0L
    private var labOwnershipPublished = false
    private var labActive = false
    private var renderProbeRequest: ChromeVisualShieldRenderProbeRequest? = null
    private var renderProbeCompleted = false
    private var renderProbeObservation: ChromeVisualShieldRenderProbeObservation? = null
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
            startSession(null, null, "start")
        }

    override fun renderProbe(
        sampleId: String,
        sourceSha256: String,
        renderContract: String,
    ): String =
        commandOnMain {
            val request = ChromeVisualShieldRenderProbeRequest(sampleId, sourceSha256, renderContract)
            if (!request.isValid()) return@commandOnMain "result=invalid_probe_request"
            startSession(request, null, "render_probe")
        }

    override fun exactDrawOracleProbe(
        sampleId: String,
        sourceSha256: String,
        renderContract: String,
    ): String =
        commandOnMain {
            val request =
                ChromeVisualShieldRenderProbeRequest(
                    sampleId = sampleId,
                    sourceSha256 = sourceSha256,
                    renderContract = renderContract,
                    exactDrawOracleRequired = true,
                )
            if (!request.isValid()) return@commandOnMain "result=invalid_oracle_probe_request"
            startSession(request, null, "exact_draw_oracle_probe")
        }

    override fun regionDiscoveryProbe(
        scenarioId: String,
        sourceSha256s: List<String>,
        renderContract: String,
    ): String =
        commandOnMain {
            val request = ChromeVisualShieldRegionDiscoveryProbeRequest(scenarioId, sourceSha256s, renderContract)
            if (!request.isValid()) return@commandOnMain "result=invalid_region_discovery_probe_request"
            startSession(null, request, "region_discovery_probe")
        }

    override fun currentRenderIdentityToken(): String? = identityGate.snapshot().context?.renderIdentityToken()

    override fun beginFixtureRender(renderGeometryKeyDigest: String?): String? =
        valueOnMain {
            val current = identityGate.snapshot().context ?: return@valueOnMain null
            invalidateProtectCapture(
                windowId = current.windowId,
                viewport = current.viewport,
                reason = ChromeVisualShieldInvalidation.Navigation,
                requireRenderAttestation = true,
            )
            identityGate.snapshot().context?.let { accepted ->
                renderGeometryKeyDigest
                    ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                    ?.let { rasterProvenanceObserver.onRenderIdentityAccepted(accepted.toProbeIdentity(), it) }
                accepted.renderIdentityToken()
            }
        }

    override fun renderAttested(
        renderIdentityToken: String,
        exactDrawOracle: ChromeVisualShieldExactDrawOracle?,
        regionDiscoveryOracle: ChromeVisualShieldRegionDiscoveryOracle?,
    ): String {
        val context = identityGate.snapshot().context ?: return "result=render_identity_unavailable"
        val currentProbe = renderProbeRequest
        if (currentProbe?.exactDrawOracleRequired == true) {
            val oracle = exactDrawOracle ?: return "result=render_oracle_missing"
            val currentIdentity = context.toProbeIdentity()
            val candidate = currentProbe.copy(exactDrawOracle = oracle)
            if (
                !candidate.isValid() ||
                ChromeVisualShieldExactDrawOracleMapper.resolve(currentIdentity, candidate) == null
            ) {
                return "result=render_oracle_mismatch"
            }
            renderProbeRequest = candidate
        }
        if (
            regionDiscoveryLab.isActive() &&
            !regionDiscoveryLab.recordOracle(renderIdentityToken, context.toProbeIdentity(), regionDiscoveryOracle)
        ) {
            return "result=region_discovery_oracle_mismatch"
        }
        if (!viewportRenderGate.recordAttestation(renderIdentityToken, context)) {
            return "result=render_identity_mismatch"
        }
        if (regionDiscoveryLab.isActive()) {
            rasterProvenanceObserver.onAttestationAccepted(context.toProbeIdentity(), regionDiscoveryOracle)
        }
        runOnMain { scheduleCaptureWhenViewportReady("render_attested") }
        return "result=render_identity_attested"
    }

    override fun stop(): String =
        commandOnMain {
            deactivate("explicit_stop")
            "result=stopped ${statusValue()}"
        }

    override fun release(): String =
        commandOnMain {
            if (renderProbeRequest != null || regionDiscoveryLab.isActive()) {
                r1Metrics.onReleaseRejected()
                return@commandOnMain "result=probe_never_release ${statusValue()}"
            }
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
            regionAnalyzer.close()
        }
    }

    private fun invalidateProtectCapture(
        windowId: Int,
        viewport: ChromeVisualViewport,
        reason: ChromeVisualShieldInvalidation,
        requireRenderAttestation: Boolean = false,
    ) {
        if (!labActive) return
        r1Metrics.onContentInvalidation()
        val current = identityGate.snapshot().context ?: return
        val hardViewportBoundary =
            reason == ChromeVisualShieldInvalidation.Viewport ||
                reason == ChromeVisualShieldInvalidation.Rotation ||
                current.windowId != windowId ||
                current.viewport != viewport
        val protected = identityGate.invalidate(windowId, viewport, regionContract, reason) ?: return
        workCoordinator.invalidateAuthority()
        regionDiscoveryLab.invalidate()
        val context = protected.context ?: return
        val waitForCurrentRender = hardViewportBoundary || requireRenderAttestation
        if (waitForCurrentRender) viewportRenderGate.requireCurrentRender(context)
        protectThenCapture(protected, reason.name.lowercase())
        if (waitForCurrentRender) cancelWorkAndJoin()
    }

    private fun invalidateWithoutNewWindow(reason: ChromeVisualShieldInvalidation) {
        val current = identityGate.snapshot().context ?: return
        r1Metrics.onContentInvalidation()
        val protected =
            identityGate.invalidate(current.windowId, current.viewport, regionContract, reason) ?: return
        workCoordinator.invalidateAuthority()
        regionDiscoveryLab.invalidate()
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
            rasterProvenanceObserver.onOpaqueCommitted(committedEpoch, current.toProbeIdentity())
            publishLabOwnership(true)
            viewportRenderGate.recordOpaqueCommit(current)
            scheduleCaptureWhenViewportReady(trigger)
        }
    }

    private fun scheduleCaptureWhenViewportReady(trigger: String) {
        val context = identityGate.snapshot().context ?: return
        if (!viewportRenderGate.consumeCapturePermission(context)) {
            log(
                "phase=viewport_wait trigger=$trigger viewportEpoch=${context.viewportEpoch} " +
                    "result=protected_pending_render_attestation",
            )
            return
        }
        scheduleCapture(trigger)
    }

    private fun scheduleCapture(trigger: String) {
        if ((renderProbeRequest != null && renderProbeCompleted) || regionDiscoveryLab.isCompleted()) return
        val identity = identityGate.beginCapture() ?: return
        rasterProvenanceObserver.onBeginCapture(identity)
        val mode =
            when {
                renderProbeRequest != null -> ChromeVisualShieldWorkMode.RenderProbe(checkNotNull(renderProbeRequest))
                regionDiscoveryLab.isActive() -> checkNotNull(regionDiscoveryLab.workModeOrNull())
                else -> ChromeVisualShieldWorkMode.Normal
            }
        if (!workCoordinator.request(ChromeVisualShieldWork(identity, trigger, mode))) {
            identityGate.failClosed(identity)
        }
    }

    private fun onCurrentDecision(sentinelMatches: Boolean) {
        if (sentinelMatches) sentinelCropMatches += 1
        captureCycles += 1
    }

    private fun deliverDecision(delivery: ChromeVisualShieldDecisionDelivery) {
        runOnMain {
            val probeMode = delivery.work.mode as? ChromeVisualShieldWorkMode.RenderProbe
            if (probeMode != null) {
                deliverRenderProbe(probeMode.request, delivery)
                return@runOnMain
            }
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

    private fun deliverRegionDiscovery(delivery: ChromeVisualShieldRegionDiscoveryDelivery) {
        runOnMain {
            log(regionDiscoveryLab.deliver(delivery))
        }
    }

    private fun deliverRenderProbe(
        request: ChromeVisualShieldRenderProbeRequest,
        delivery: ChromeVisualShieldDecisionDelivery,
    ) {
        val cropEvidence = checkNotNull(delivery.cropEvidence)
        val result = renderProbeAuthority.observe(delivery.work.identity, delivery.decision)
        renderProbeCompleted =
            result != ChromeVisualShieldRenderProbeResult.StaleDropped &&
            result != ChromeVisualShieldRenderProbeResult.IdentityMismatchRejected
        val decision = delivery.decision
        renderProbeObservation =
            ChromeVisualShieldRenderProbeObservation(
                request = request,
                identity = delivery.work.identity,
                analyzedRegion = delivery.analyzedRegion,
                crop = cropEvidence,
                result = result,
                action = decision.logValue(),
                reason = decision.reason,
                filterProbability = decision.filterProbability,
                inferenceCount = r1Metrics.snapshot().inferenceCompleted,
            )
        if (renderProbeCompleted) onCurrentDecision(delivery.sentinelMatches)
        log(
            "phase=render_probe sample=${request.sampleId} sourceSha=${request.sourceSha256} " +
                "renderContract=${request.renderContract} viewport=${delivery.work.identity.viewport} " +
                "region=${delivery.analyzedRegion} crop=${cropEvidence.width}x${cropEvidence.height} " +
                "cropSha=${cropEvidence.rgbaSha256} action=${decision.logValue()} " +
                "reason=${decision.reason} filterProbability=${decision.filterProbability} " +
                "inferenceCount=${r1Metrics.snapshot().inferenceCompleted} result=$result neverRelease=true rawPresented=false",
        )
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
        viewportRenderGate.reset()
        surface.close()
        pendingCoverEpoch = null
        labActive = false
        renderProbeRequest = null
        renderProbeCompleted = false
        regionDiscoveryLab.clear()
        rasterProvenanceObserver.onSessionEnded()
        publishLabOwnership(false)
        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
        log("phase=inactive reason=$reason rawPresented=false")
        logMetrics("deactivate")
    }

    private fun statusValue(): String {
        val base =
            ChromeVisualShieldStatusFormatter.format(
                active = labActive,
                state = identityGate.snapshot(),
                metrics = metrics.snapshot(),
                r1 = r1Metrics.snapshot(),
                captureCycles = captureCycles,
                opaqueCommitted = opaqueCommittedCount,
                sentinelCropMatches = sentinelCropMatches,
                workIdle = workCoordinator.isIdle(),
                probeActive = renderProbeRequest != null,
                probeCompleted = renderProbeCompleted,
                probe = renderProbeObservation,
            )
        return "$base ${regionDiscoveryLab.statusValue()} ${rasterProvenanceObserver.statusValue()}"
    }

    private fun startSession(
        probe: ChromeVisualShieldRenderProbeRequest?,
        discoveryProbe: ChromeVisualShieldRegionDiscoveryProbeRequest?,
        trigger: String,
    ): String {
        if (!enabled) return "result=disabled"
        val window = windowInspector.find(AnyWindowId) ?: return "result=chrome_absent"
        val viewport = windowInspector.viewport(window) ?: return "result=viewport_absent"
        cancelWorkAndJoin()
        viewportRenderGate.reset()
        renderProbeRequest = probe
        renderProbeCompleted = false
        if (probe != null) renderProbeObservation = null
        regionDiscoveryLab.begin(discoveryProbe)
        rasterProvenanceObserver.reset(discoveryProbe != null)
        labActive = true
        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(true)
        val started =
            identityGate.start(window.id, viewport, regionContract)
                ?: return "result=invalid_fixture_contract"
        if (probe?.exactDrawOracleRequired == true || discoveryProbe != null) {
            started.context?.let(viewportRenderGate::requireCurrentRender)
        }
        if (!protectThenCapture(started, trigger)) {
            labActive = false
            renderProbeRequest = null
            regionDiscoveryLab.clear()
            identityGate.stop()
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
            return "result=surface_failed ${statusValue()}"
        }
        return "result=${if (probe == null && discoveryProbe == null) "started" else "probe_started"} ${statusValue()}"
    }

    private suspend fun executeWork(work: ChromeVisualShieldWork) {
        when (work.mode) {
            is ChromeVisualShieldWorkMode.RegionDiscoveryProbe -> regionDiscoveryProcessor.execute(work)
            else -> workProcessor.execute(work)
        }
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

    private fun <T> valueOnMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = java.util.concurrent.FutureTask(block)
        service.mainExecutor.execute(task)
        return runCatching { task.get(MainCommandTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS) }
            .getOrNull()
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

    private fun ChromeVisualShieldContext.toProbeIdentity(): ChromeVisualShieldIdentity =
        ChromeVisualShieldIdentity(
            protectionSessionId = protectionSessionId,
            windowId = windowId,
            contentEpoch = contentEpoch,
            viewport = viewport,
            viewportEpoch = viewportEpoch,
            captureSequence = identityGate.snapshot().nextCaptureSequence,
            regionId = regionId,
            regionSequence = regionSequence,
            region = region,
        )

    private fun log(message: String) {
        Log.i(LogTag, "$message gloshIAConnected=true")
    }

    private companion object {
        const val AnyWindowId = -1
        const val LogTag = "GloshVisualShield"
        const val MainCommandTimeoutSeconds = 5L
    }
}
