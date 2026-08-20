package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.contentfilter.feature.accessibility.ChromeVisualGloshiaEngineProvider
import com.contentfilter.feature.accessibility.R
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.LifecycleGloshiaVisualAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

internal class ChromeVisualController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val enabled =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            service.resources.getBoolean(R.bool.chrome_visual_images_enabled) &&
            ChromeVisualGloshiaEngineProvider.isAvailableInCurrentProcess()
    private val capture = ChromeWindowCapture(service)
    private val overlay = ChromeVisualOverlay(service)
    private val lock = Any()
    private val identityGate = ChromeVisualIdentityGate()
    private val decisionCache = ChromeVisualDecisionCache(MaxDecisionCacheEntries)
    private val pageBlockLedger = ChromeVisualPageBlockLedger()
    private val inferenceMutex = Mutex()
    private var analyzer: GloshiaVisualAnalyzer? = null
    private var activeJob: Job? = null
    private var verificationJob: Job? = null
    private var pendingSinceMillis = 0L
    private var lastTileSignatures = emptyMap<String, Long>()

    @Volatile
    private var stableVerificationCount = 0

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        if (event.packageName?.toString() != ChromePackageName) {
            val inputMethodTop = inputMethodTop()
            if (inputMethodTop != null && findChromeWindow(AnyWindowId, allowBehindInputMethod = true) != null) {
                overlay.clipBottom(inputMethodTop)
            } else if (findChromeWindow(AnyWindowId) == null) {
                deactivate()
            }
            return
        }
        val windowId = event.windowId.takeIf { it >= 0 } ?: return
        val window = findChromeWindow(windowId) ?: return
        val viewport = viewport(window) ?: return
        beginPage(pageIdentity(window))
        stableVerificationCount = 0
        val eventAt = SystemClock.elapsedRealtime()
        val coverageTiles = fallbackTiles(viewport)
        coverageTiles.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
        overlay.retain(coverageTiles.mapTo(mutableSetOf(), ChromeVisualRegion::id))
        clipForInputMethod()
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
            activeJob = scope.launch { analyzeAfterSettle(windowId, eventAt, settleMillis) }
        }
        if (startsBurst) {
            Log.i(
                LogTag,
                "windowId=$windowId phase=precover coverMs=${SystemClock.elapsedRealtime() - eventAt} " +
                    "regions=${coverageTiles.size} result=success",
            )
        }
    }

    override fun close() {
        deactivate()
        synchronized(lock) {
            (analyzer as? Closeable)?.close()
            analyzer = null
        }
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
        }
        overlay.close()
        decisionCache.clear()
        pageBlockLedger.clear()
        lastTileSignatures = emptyMap()
        stableVerificationCount = 0
    }

    private suspend fun analyzeAfterSettle(
        windowId: Int,
        eventAt: Long,
        settleMillis: Long,
    ) {
        delay(settleMillis)
        val window = withContext(Dispatchers.Main.immediate) { findChromeWindow(windowId) } ?: return
        val viewport = withContext(Dispatchers.Main.immediate) { viewport(window) } ?: return
        val pageIdentity = pageIdentity(window)
        beginPage(pageIdentity)
        val candidates = withContext(Dispatchers.Main.immediate) { collectCandidates(window) }
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
            capture.capture(window.id) ?: run {
                Log.i(LogTag, "windowId=${window.id} phase=capture result=failed")
                markEventAnalysisComplete()
                scheduleVerification(window.id)
                return
            }
        try {
            val topInset = (FallbackTopInsetDp * metrics.density).toInt()
            val fallback = ChromeVisualRegionPlanner.fallbackTiles(viewport, topInset)
            lastTileSignatures = signatures(frame.bitmap, viewport, fallback)
            val regions = (provisional + fallback).distinctBy(ChromeVisualRegion::id)
            if (regions.isEmpty()) {
                withContext(Dispatchers.Main.immediate) { overlay.retain(emptySet()) }
                log(window.id, frame, startedAt, 0, 0, "no_region")
                markEventAnalysisComplete()
                scheduleVerification(window.id)
                return
            }
            withContext(Dispatchers.Main.immediate) {
                fallback.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
                overlay.retain(regions.mapTo(mutableSetOf(), ChromeVisualRegion::id))
                clipForInputMethod()
            }
            val counts = evaluateRegions(window.id, pageIdentity, frame, viewport, regions)
            log(window.id, frame, startedAt, counts.allowed, counts.blocked, "success")
            markEventAnalysisComplete()
            scheduleVerification(window.id)
        } finally {
            frame.close()
        }
    }

    private suspend fun verifyVisualChanges(windowId: Int) {
        val window = withContext(Dispatchers.Main.immediate) { findChromeWindow(windowId) } ?: return
        val viewport = withContext(Dispatchers.Main.immediate) { viewport(window) } ?: return
        val pageIdentity = pageIdentity(window)
        beginPage(pageIdentity)
        val frame =
            capture.capture(window.id) ?: run {
                scheduleVerification(window.id)
                return
            }
        try {
            val tiles = fallbackTiles(viewport)
            val current = signatures(frame.bitmap, viewport, tiles)
            val previous = lastTileSignatures
            val changed =
                ChromeVisualRegionPlanner.changedFallbackTiles(
                    viewport,
                    (FallbackTopInsetDp * service.resources.displayMetrics.density).toInt(),
                    previous,
                    current,
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
            withContext(Dispatchers.Main.immediate) {
                changed.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
            }
            val counts = evaluateRegions(window.id, pageIdentity, frame, viewport, changed)
            lastTileSignatures =
                ChromeVisualSignatureLedger.advance(
                    previous,
                    current,
                    changed.mapTo(mutableSetOf(), ChromeVisualRegion::id),
                )
            Log.i(
                LogTag,
                "windowId=${window.id} phase=verify captureMs=${frame.latencyMillis} changed=${changed.size} " +
                    "allowed=${counts.allowed} blocked=${counts.blocked} result=updated",
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
    ): RegionCounts {
        val captureIdentity = synchronized(lock) { identityGate.nextCapture() }
        var blocked = 0
        var allowed = 0
        val fallbackTiles = fallbackTiles(viewport)
        for (region in regions) {
            val frameRegion = ChromeVisualGeometryMapper.toFrame(region, viewport, frame.width, frame.height) ?: continue
            val signature = signature(frame.bitmap, frameRegion, region) ?: continue
            val identity =
                ChromeVisualIdentity(
                    windowId = windowId,
                    contentEpoch = captureIdentity.first,
                    captureSequence = captureIdentity.second,
                    regionId = region.id,
                    visualSignature = signature,
                )
            val action =
                decisionCache[signature] ?: analyze(frame.bitmap, frameRegion).also {
                    decisionCache[signature] = it
                }
            if (!synchronized(lock) { identityGate.isCurrent(identity) }) return RegionCounts(allowed, blocked)
            withContext(Dispatchers.Main.immediate) {
                when (action) {
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
                clipForInputMethod()
            }
            if (action == GloshiaVisualAction.Block) blocked++ else allowed++
        }
        return RegionCounts(allowed, blocked)
    }

    private suspend fun analyze(
        source: Bitmap,
        region: ChromeVisualRegion,
    ): GloshiaVisualAction =
        inferenceMutex.withLock {
            val crop =
                runCatching {
                    Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
                }.getOrNull() ?: return@withLock GloshiaVisualAction.Block
            val prepared =
                try {
                    AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
                        crop,
                        maxOf(crop.width, crop.height),
                    )
                } finally {
                    crop.recycle()
                } ?: return@withLock GloshiaVisualAction.Block
            return try {
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = region.id,
                    preparedImages = listOf(prepared),
                    analyzer = engine() ?: return@withLock GloshiaVisualAction.Block,
                ).action
            } finally {
                prepared.rgb888.fill(0)
            }
        }

    private fun engine(): GloshiaVisualAnalyzer? =
        synchronized(lock) {
            analyzer
                ?: ChromeVisualGloshiaEngineProvider.create(service)?.let {
                    LifecycleGloshiaVisualAnalyzer(it).also { created -> analyzer = created }
                }
        }

    private fun collectCandidates(window: AccessibilityWindowInfo): List<ChromeVisualNodeCandidate> {
        val root = window.root ?: return emptyList()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val result = mutableListOf<ChromeVisualNodeCandidate>()
        queue += root
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MaxAccessibilityNodes) {
            val node = queue.removeFirst()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                result +=
                    ChromeVisualNodeCandidate(
                        className = node.className?.toString().orEmpty(),
                        hasDescription = !node.contentDescription.isNullOrBlank(),
                        childCount = node.childCount,
                        region =
                            ChromeVisualRegion(
                                id = "node_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}",
                                left = rect.left,
                                top = rect.top,
                                right = rect.right,
                                bottom = rect.bottom,
                            ),
                    )
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return result
    }

    private fun findChromeWindow(
        requestedWindowId: Int,
        allowBehindInputMethod: Boolean = false,
    ): AccessibilityWindowInfo? {
        val candidates =
            service.windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == ChromePackageName
            }
        return candidates.firstOrNull { requestedWindowId != AnyWindowId && it.id == requestedWindowId }
            ?: candidates.firstOrNull { it.isActive }
            ?: candidates.firstOrNull { it.isFocused }
            ?: candidates.firstOrNull().takeIf { allowBehindInputMethod }
    }

    private fun inputMethodTop(): Int? =
        service.windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                val bounds = Rect()
                window.root?.getBoundsInScreen(bounds)
                bounds.top.takeIf { !bounds.isEmpty }
            }
            .minOrNull()

    private fun clipForInputMethod() {
        inputMethodTop()?.let(overlay::clipBottom)
    }

    private fun pageIdentity(window: AccessibilityWindowInfo): Long {
        var hash = FnvOffsetBasis
        val value = window.title?.toString().orEmpty().ifBlank { "window:${window.id}" }
        value.forEach { character -> hash = (hash xor character.code.toLong()) * FnvPrime }
        return hash
    }

    private fun beginPage(identity: Long) {
        if (pageBlockLedger.beginPage(identity)) {
            decisionCache.clear()
            lastTileSignatures = emptyMap()
            stableVerificationCount = 0
        }
    }

    private fun viewport(window: AccessibilityWindowInfo): ChromeVisualViewport? {
        val root = window.root ?: return null
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return ChromeVisualViewport(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .takeIf { it.width > 0 && it.height > 0 }
    }

    private fun signature(
        bitmap: Bitmap,
        frameRegion: ChromeVisualRegion,
        screenRegion: ChromeVisualRegion,
    ): Long? {
        if (frameRegion.width <= 0 || frameRegion.height <= 0) return null
        var hash = FnvOffsetBasis
        hash = (hash xor screenRegion.left.toLong()) * FnvPrime
        hash = (hash xor screenRegion.top.toLong()) * FnvPrime
        hash = (hash xor screenRegion.width.toLong()) * FnvPrime
        hash = (hash xor screenRegion.height.toLong()) * FnvPrime
        repeat(SignatureRows) { row ->
            val y = frameRegion.top + ((row + 0.5) * frameRegion.height / SignatureRows).toInt()
            repeat(SignatureColumns) { column ->
                val x = frameRegion.left + ((column + 0.5) * frameRegion.width / SignatureColumns).toInt()
                hash = (hash xor bitmap.getPixel(x, y).toLong()) * FnvPrime
            }
        }
        return hash
    }

    private fun signatures(
        bitmap: Bitmap,
        viewport: ChromeVisualViewport,
        regions: List<ChromeVisualRegion>,
    ): Map<String, Long> =
        regions.mapNotNull { region ->
            val frameRegion =
                ChromeVisualGeometryMapper.toFrame(region, viewport, bitmap.width, bitmap.height)
                    ?: return@mapNotNull null
            signature(bitmap, frameRegion, region)?.let { region.id to it }
        }.toMap()

    private fun fallbackTiles(viewport: ChromeVisualViewport): List<ChromeVisualRegion> {
        val metrics = service.resources.displayMetrics
        return ChromeVisualRegionPlanner.fallbackTiles(
            viewport,
            (FallbackTopInsetDp * metrics.density).toInt(),
        )
    }

    private fun scheduleVerification(windowId: Int) {
        val job =
            scope.launch {
                delay(ChromeVisualVerificationSchedule.delayMillis(stableVerificationCount))
                verifyVisualChanges(windowId)
            }
        synchronized(lock) {
            verificationJob?.cancel()
            verificationJob = job
        }
    }

    private fun markEventAnalysisComplete() {
        synchronized(lock) { pendingSinceMillis = 0L }
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
        const val ChromePackageName = "com.android.chrome"
        const val AnyWindowId = -1
        const val ContentSettleMillis = 150L
        const val MaximumSettleMillis = 500L
        const val MinimumRegionDp = 48
        const val FallbackTopInsetDp = 96
        const val MaxAccessibilityNodes = 400
        const val MaxDecisionCacheEntries = 128
        const val SignatureColumns = 24
        const val SignatureRows = 16
        const val FnvOffsetBasis = -3750763034362895579L
        const val FnvPrime = 1099511628211L
        const val LogTag = "ChromeVisual"
    }
}
