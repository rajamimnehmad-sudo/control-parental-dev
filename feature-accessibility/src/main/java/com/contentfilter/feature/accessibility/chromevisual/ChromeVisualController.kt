package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.feature.accessibility.ChromeVisualGloshiaEngineProvider
import com.contentfilter.feature.accessibility.R
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ChromeVisualController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val initialCapability =
        ChromeVisualCapabilityPolicy.initial(
            sdkInt = Build.VERSION.SDK_INT,
            is64BitProcess = ChromeVisualGloshiaEngineProvider.isAvailableInCurrentProcess(),
            featureEnabled = service.resources.getBoolean(R.bool.chrome_visual_images_enabled),
            engineAvailable = ChromeVisualGloshiaEngineProvider.isAvailableInCurrentProcess(),
        )
    private val enabled = initialCapability.canAnalyzeChrome
    private val capture = ChromeWindowCapture(service)
    private val overlay = ChromeVisualOverlay(service)
    private val windowInspector = ChromeVisualWindowInspector(service)
    private val lock = Any()
    private val identityGate = ChromeVisualIdentityGate()
    private val decisionCache = ChromeVisualDecisionCache(MaxDecisionCacheEntries)
    private val pageBlockLedger = ChromeVisualPageBlockLedger()
    private val videoPolicy = ChromeVisualVideoPolicy()
    private val baselineCoordinator = ChromeVisualBaselineCoordinator()
    private val atomicReplayCoordinator = ChromeVisualAtomicReplayCoordinator()
    private val regionAnalyzer = ChromeVisualRegionAnalyzer(service)
    private var activeJob: Job? = null
    private var verificationJob: Job? = null
    private var pendingSinceMillis = 0L
    private var lastFallbackReason: String? = null

    init {
        if (!enabled) logFallback(initialCapability)
    }

    @Volatile
    private var lastTileSignatures = emptyMap<String, Long>()

    @Volatile
    private var activeWindowId = AnyWindowId

    @Volatile
    private var lastViewport: ChromeVisualViewport? = null

    @Volatile
    private var stableVerificationCount = 0

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        if (!windowInspector.isChromePackage(event.packageName)) {
            val inputMethodTop = windowInspector.inputMethodTop()
            if (inputMethodTop != null && windowInspector.find(AnyWindowId, allowBehindInputMethod = true) != null) {
                overlay.clipBottom(inputMethodTop)
            } else if (windowInspector.find(AnyWindowId) == null) {
                deactivate()
            }
            return
        }
        val windowId = event.windowId.takeIf { it >= 0 } ?: return
        val window =
            windowInspector.find(windowId) ?: run {
                logFallback(ChromeVisualCapabilityPolicy.runtimeUnavailable())
                return
            }
        val viewport =
            windowInspector.viewport(window) ?: run {
                logFallback(ChromeVisualCapabilityPolicy.runtimeUnavailable(ambiguousGeometry = true))
                return
            }
        val currentContext = ChromeVisualBaselineContext(windowId, windowInspector.pageIdentity(window), viewport)
        val pageChanged = beginPage(currentContext.pageIdentity)
        val atomicMutation = ChromeVisualAtomicMutationPolicy.requiresReplay(event)
        val geometryRestart = ChromeVisualAtomicMutationPolicy.requiresGeometryRestart(event.eventType)
        if (atomicMutation) {
            atomicReplayCoordinator.request()
            synchronized(lock) {
                verificationJob?.cancel()
                verificationJob = null
                identityGate.invalidate(windowId)
                if (geometryRestart) {
                    activeJob?.cancel()
                    activeJob = null
                }
            }
            if (geometryRestart) {
                baselineCoordinator.cancelIfActive(currentContext)
            } else if (baselineCoordinator.coalesceIfActive(currentContext)) {
                precover(viewport)
                return
            }
        } else if (baselineCoordinator.coalesceIfActive(currentContext)) {
            return
        }
        val requiresBaseline =
            ChromeVisualEventModePolicy.requiresBaseline(
                pageChanged,
                activeWindowId,
                windowId,
                lastViewport,
                viewport,
                lastTileSignatures.isNotEmpty(),
            )
        activeWindowId = windowId
        lastViewport = viewport
        stableVerificationCount = 0
        val eventAt = SystemClock.elapsedRealtime()
        val coverageTiles = if (requiresBaseline || atomicMutation) precover(viewport) else emptyList()
        var startsBurst = false
        synchronized(lock) {
            verificationJob?.cancel()
            verificationJob = null
            identityGate.invalidate(windowId)
            if (pendingSinceMillis == 0L) {
                pendingSinceMillis = eventAt
                startsBurst = true
            }
            val elapsedPending = eventAt - pendingSinceMillis
            val settleMillis = minOf(ContentSettleMillis, (MaximumSettleMillis - elapsedPending).coerceAtLeast(0L))
            activeJob?.cancel()
            if (requiresBaseline) baselineCoordinator.replace(currentContext)
            activeJob =
                scope.launch {
                    if (requiresBaseline) {
                        runBaseline(currentContext, eventAt, settleMillis)
                    } else {
                        delay(settleMillis)
                        markEventAnalysisComplete()
                        verifyVisualChanges(windowId)
                    }
                }
        }
        if (startsBurst && coverageTiles.isNotEmpty()) {
            Log.i(
                LogTag,
                "windowId=$windowId phase=precover coverMs=${SystemClock.elapsedRealtime() - eventAt} " +
                    "regions=${coverageTiles.size} trigger=${if (requiresBaseline) "baseline" else "atomic_replay"} " +
                    "result=success",
            )
        }
    }

    override fun close() {
        deactivate()
        regionAnalyzer.close()
        decisionCache.clear()
    }

    private fun deactivate() {
        synchronized(lock) {
            activeJob?.cancel()
            activeJob = null
            verificationJob?.cancel()
            verificationJob = null
            pendingSinceMillis = 0L
            identityGate.invalidate(AnyWindowId)
            activeWindowId = AnyWindowId
            lastViewport = null
            baselineCoordinator.clear()
            atomicReplayCoordinator.clear()
        }
        overlay.close()
        decisionCache.clear()
        pageBlockLedger.clear()
        videoPolicy.clear()
        lastTileSignatures = emptyMap()
        stableVerificationCount = 0
    }

    private suspend fun analyzeAfterSettle(
        windowId: Int,
        eventAt: Long,
        settleMillis: Long,
    ) {
        delay(settleMillis)
        val window = withContext(Dispatchers.Main.immediate) { windowInspector.find(windowId) } ?: return
        val viewport = withContext(Dispatchers.Main.immediate) { windowInspector.viewport(window) } ?: return
        val pageIdentity = windowInspector.pageIdentity(window)
        beginPage(pageIdentity)
        activeWindowId = window.id
        lastViewport = viewport
        val candidates = withContext(Dispatchers.Main.immediate) { windowInspector.collectCandidates(window) }
        val metrics = service.resources.displayMetrics
        val minimumEdge = (MinimumRegionDp * metrics.density).toInt().coerceAtLeast(1)
        val provisional =
            ChromeVisualRegionPlanner.fromNodes(
                candidates = candidates,
                viewport = viewport,
                minimumEdge = minimumEdge,
            )
        withContext(Dispatchers.Main.immediate) {
            val initialCoverage = fallbackTiles(viewport)
            provisional.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
            overlay.retain((provisional + initialCoverage).mapTo(mutableSetOf(), ChromeVisualRegion::id))
            clipForInputMethod()
        }
        Log.i(
            LogTag,
            "windowId=${window.id} phase=cover coverMs=${SystemClock.elapsedRealtime() - eventAt} " +
                "regions=${provisional.size} result=success",
        )
        val startedAt = SystemClock.elapsedRealtime()
        val frame =
            when (val result = capture.capture(window.id)) {
                is ChromeWindowCaptureResult.Captured -> result.frame
                is ChromeWindowCaptureResult.Failed -> {
                    Log.i(LogTag, "windowId=${window.id} phase=capture result=failed")
                    logFallback(
                        ChromeVisualCapabilityPolicy.captureFailure(
                            secureWindow =
                                result.errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
                        ),
                    )
                    coverFailedVideoRegions()
                    markEventAnalysisComplete()
                    scheduleVerification(window.id)
                    return
                }
            }
        try {
            val topInset = (FallbackTopInsetDp * metrics.density).toInt()
            val fallback = ChromeVisualRegionPlanner.fallbackTiles(viewport, topInset)
            lastTileSignatures = ChromeVisualFrameSignature.all(frame.bitmap, viewport, fallback)
            val regions = (provisional + fallback).distinctBy(ChromeVisualRegion::id)
            if (regions.isEmpty()) {
                withContext(Dispatchers.Main.immediate) { overlay.retain(emptySet()) }
                log(window.id, frame, startedAt, 0, 0, "no_region")
                markEventAnalysisComplete()
                scheduleVerification(window.id)
                return
            }
            withContext(Dispatchers.Main.immediate) {
                overlay.retain(regions.mapTo(mutableSetOf(), ChromeVisualRegion::id))
                clipForInputMethod()
            }
            val counts = evaluateRegions(window.id, pageIdentity, frame, viewport, regions, emptySet())
            log(window.id, frame, startedAt, counts.allowed, counts.blocked, "success")
            markEventAnalysisComplete()
            scheduleVerification(window.id)
        } finally {
            frame.close()
        }
    }

    private suspend fun verifyVisualChanges(windowId: Int) {
        val window = withContext(Dispatchers.Main.immediate) { windowInspector.find(windowId) } ?: return
        val viewport = withContext(Dispatchers.Main.immediate) { windowInspector.viewport(window) } ?: return
        val pageIdentity = windowInspector.pageIdentity(window)
        val pageChanged = beginPage(pageIdentity)
        if (pageChanged || activeWindowId != window.id || lastViewport != viewport || lastTileSignatures.isEmpty()) {
            val context = ChromeVisualBaselineContext(window.id, pageIdentity, viewport)
            if (!baselineCoordinator.startIfIdle(context)) return
            activeWindowId = window.id
            lastViewport = viewport
            withContext(Dispatchers.Main.immediate) { precover(viewport) }
            runBaseline(context, SystemClock.elapsedRealtime(), 0L)
            return
        }
        val replayRevision = atomicReplayCoordinator.currentRevision()
        val frame =
            when (val result = capture.capture(window.id)) {
                is ChromeWindowCaptureResult.Captured -> result.frame
                is ChromeWindowCaptureResult.Failed -> {
                    logFallback(
                        ChromeVisualCapabilityPolicy.captureFailure(
                            secureWindow =
                                result.errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
                        ),
                    )
                    coverFailedVideoRegions()
                    scheduleVerification(window.id)
                    return
                }
            }
        try {
            val tiles = fallbackTiles(viewport)
            val current = ChromeVisualFrameSignature.all(frame.bitmap, viewport, tiles)
            val previous = lastTileSignatures
            val visuallyChanged =
                ChromeVisualRegionPlanner.changedFallbackTiles(
                    viewport,
                    (FallbackTopInsetDp * service.resources.displayMetrics.density).toInt(),
                    previous,
                    current,
                )
            val confirmations = videoPolicy.regionsNeedingConfirmation(window.id, pageIdentity, tiles)
            val changed =
                ChromeVisualReplayRegionPolicy.select(
                    replayActive = replayRevision != null,
                    fallbackTiles = tiles,
                    visuallyChanged = visuallyChanged,
                    confirmations = confirmations,
                )
            if (changed.isEmpty()) {
                lastTileSignatures = current
                stableVerificationCount += 1
                Log.i(
                    LogTag,
                    "windowId=${window.id} phase=verify captureMs=${frame.latencyMillis} changed=0 result=stable",
                )
                scheduleVerification(window.id)
                return
            }
            stableVerificationCount = 0
            synchronized(lock) { identityGate.invalidate(window.id) }
            val observedChanges = visuallyChanged.mapTo(mutableSetOf(), ChromeVisualRegion::id)
            val counts = evaluateRegions(window.id, pageIdentity, frame, viewport, changed, observedChanges)
            lastTileSignatures =
                ChromeVisualSignatureLedger.advance(
                    previous,
                    current,
                    counts.processedRegionIds,
                )
            val replayCompleted =
                replayRevision?.let { revision ->
                    counts.completed && atomicReplayCoordinator.complete(revision)
                } == true
            Log.i(
                LogTag,
                "windowId=${window.id} phase=verify captureMs=${frame.latencyMillis} changed=${changed.size} " +
                    "allowed=${counts.allowed} blocked=${counts.blocked} " +
                    "replay=${if (replayRevision == null) "none" else if (replayCompleted) "complete" else "pending"} " +
                    "result=updated",
            )
            scheduleVerification(window.id)
        } finally {
            frame.close()
        }
    }

    private suspend fun evaluateRegions(
        windowId: Int,
        pageIdentity: Long,
        frame: ChromeWindowFrame,
        viewport: ChromeVisualViewport,
        regions: List<ChromeVisualRegion>,
        observedChanges: Set<String>,
    ): RegionCounts {
        val captureIdentity = synchronized(lock) { identityGate.nextCapture() }
        var blocked = 0
        var allowed = 0
        var completed = true
        val processedRegionIds = mutableSetOf<String>()
        val fallbackTiles = fallbackTiles(viewport)
        for (region in regions) {
            val temporal = region.id.startsWith(FallbackRegionPrefix)
            val videoKey = ChromeVisualVideoRegionKey(windowId, pageIdentity, region.id)
            if (
                temporal &&
                videoPolicy.beforeSample(videoKey, region, region.id in observedChanges) ==
                ChromeVisualPresentation.Covered
            ) {
                withContext(Dispatchers.Main.immediate) {
                    overlay.show(region, ChromeVisualOverlayState.Pending)
                    clipForInputMethod()
                }
            }
            val frameRegion = ChromeVisualGeometryMapper.toFrame(region, viewport, frame.width, frame.height)
            if (frameRegion == null) {
                completed = false
                continue
            }
            val signature = ChromeVisualFrameSignature.one(frame.bitmap, frameRegion, region)
            if (signature == null) {
                completed = false
                continue
            }
            val identity =
                ChromeVisualIdentity(
                    windowId = windowId,
                    contentEpoch = captureIdentity.first,
                    captureSequence = captureIdentity.second,
                    regionId = region.id,
                    region = region,
                    visualSignature = signature,
                )
            val decision =
                decisionCache[signature] ?: regionAnalyzer.analyze(frame.bitmap, frameRegion).also {
                    decisionCache[signature] = it
                }
            val applied =
                withContext(Dispatchers.Main.immediate) {
                    val windowStillCurrent =
                        windowInspector.find(windowId)?.let {
                            windowInspector.pageIdentity(it) == pageIdentity && windowInspector.viewport(it) == viewport
                        } == true
                    val identityStillCurrent = synchronized(lock) { identityGate.isCurrent(identity) }
                    if (!windowStillCurrent || !identityStillCurrent) {
                        false
                    } else {
                        if (temporal) {
                            when (videoPolicy.record(videoKey, decision.toSampleDecision())) {
                                ChromeVisualPresentation.Visible -> overlay.remove(region.id)
                                ChromeVisualPresentation.Covered ->
                                    overlay.show(region, ChromeVisualOverlayState.Blocked)
                            }
                        } else {
                            when (decision.action) {
                                GloshiaVisualAction.Allow ->
                                    if (pageBlockLedger.mustRemainBlocked(pageIdentity, region.id)) {
                                        overlay.show(region, ChromeVisualOverlayState.Blocked)
                                    } else {
                                        overlay.remove(region.id)
                                    }
                                GloshiaVisualAction.Block -> {
                                    pageBlockLedger.recordBlocked(pageIdentity, region, fallbackTiles)
                                    overlay.show(region, ChromeVisualOverlayState.Blocked)
                                }
                            }
                        }
                        clipForInputMethod()
                        true
                    }
                }
            if (!applied) {
                return RegionCounts(allowed, blocked, completed = false, processedRegionIds = processedRegionIds)
            }
            processedRegionIds += region.id
            if (decision.action == GloshiaVisualAction.Block) blocked++ else allowed++
        }
        return RegionCounts(allowed, blocked, completed, processedRegionIds)
    }

    private fun clipForInputMethod() {
        windowInspector.inputMethodTop()?.let(overlay::clipBottom)
    }

    private fun beginPage(identity: Long): Boolean {
        val changed = pageBlockLedger.beginPage(identity)
        videoPolicy.beginPage(identity)
        if (changed) {
            decisionCache.clear()
            lastTileSignatures = emptyMap()
            stableVerificationCount = 0
            atomicReplayCoordinator.clear()
        }
        return changed
    }

    private fun fallbackTiles(viewport: ChromeVisualViewport): List<ChromeVisualRegion> {
        val metrics = service.resources.displayMetrics
        return ChromeVisualRegionPlanner.fallbackTiles(
            viewport,
            (FallbackTopInsetDp * metrics.density).toInt(),
        )
    }

    private fun precover(viewport: ChromeVisualViewport): List<ChromeVisualRegion> {
        val coverage = fallbackTiles(viewport)
        coverage.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
        overlay.retain(coverage.mapTo(mutableSetOf(), ChromeVisualRegion::id))
        clipForInputMethod()
        return coverage
    }

    private fun scheduleVerification(windowId: Int) {
        val job =
            scope.launch {
                delay(
                    ChromeVisualVerificationSchedule.delayMillis(
                        stableVerificationCount,
                        videoPolicy.hasDynamicRegions(),
                    ),
                )
                verifyVisualChanges(windowId)
            }
        synchronized(lock) {
            verificationJob?.cancel()
            verificationJob = job
        }
    }

    private suspend fun coverFailedVideoRegions() {
        val failed = videoPolicy.failActiveRegions()
        withContext(Dispatchers.Main.immediate) {
            failed.forEach { overlay.show(it, ChromeVisualOverlayState.Blocked) }
            clipForInputMethod()
        }
    }

    private fun GloshiaVisualDecision.toSampleDecision(): ChromeVisualSampleDecision =
        when {
            action == GloshiaVisualAction.Allow -> ChromeVisualSampleDecision.Allow
            reason == GloshiaVisualPolicyContract.ModelFilterReason -> ChromeVisualSampleDecision.Block
            else -> ChromeVisualSampleDecision.Unavailable
        }

    private fun markEventAnalysisComplete() {
        synchronized(lock) { pendingSinceMillis = 0L }
    }

    private fun logFallback(decision: ChromeVisualCapabilityDecision) {
        if (lastFallbackReason == decision.reason) return
        lastFallbackReason = decision.reason
        Log.i(
            LogTag,
            "capability=${decision.state.name} reason=${decision.reason} " +
                "keepCoverage=${decision.keepExistingCoverage} fallback=dag_required",
        )
    }

    private suspend fun runBaseline(
        context: ChromeVisualBaselineContext,
        eventAt: Long,
        settleMillis: Long,
    ) {
        try {
            analyzeAfterSettle(context.windowId, eventAt, settleMillis)
        } finally {
            if (baselineCoordinator.finish(context)) scheduleVerification(context.windowId)
        }
    }

    private fun log(
        windowId: Int,
        frame: ChromeWindowFrame,
        startedAt: Long,
        allowed: Int,
        blocked: Int,
        result: String,
    ) {
        Log.i(
            LogTag,
            "windowId=$windowId captureMs=${frame.latencyMillis} totalMs=${SystemClock.elapsedRealtime() - startedAt} " +
                "width=${frame.width} height=${frame.height} temporaryBytes=${frame.temporaryBytes} " +
                "allowed=$allowed blocked=$blocked result=$result",
        )
    }

    private companion object {
        const val AnyWindowId = -1
        const val ContentSettleMillis = 150L
        const val MaximumSettleMillis = 500L
        const val MinimumRegionDp = 48
        const val FallbackTopInsetDp = 96
        const val MaxDecisionCacheEntries = 128
        const val FallbackRegionPrefix = "tile_"
        const val LogTag = "ChromeVisual"
    }
}
