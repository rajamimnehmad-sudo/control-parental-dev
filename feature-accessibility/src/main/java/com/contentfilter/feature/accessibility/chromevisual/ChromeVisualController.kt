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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.LinkedHashMap

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
    private val decisionCache = VisualDecisionCache(MaxDecisionCacheEntries)
    private val inferenceMutex = Mutex()
    private var analyzer: GloshiaVisualAnalyzer? = null
    private var activeJob: Job? = null
    private var pendingSinceMillis = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || event.packageName?.toString() != ChromePackageName) return
        val windowId = event.windowId.takeIf { it >= 0 } ?: return
        val eventAt = SystemClock.elapsedRealtime()
        val coverageTiles = fallbackTilesForDisplay()
        coverageTiles.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
        overlay.retain(coverageTiles.mapTo(mutableSetOf(), ChromeVisualRegion::id))
        var startsBurst = false
        synchronized(lock) {
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
        synchronized(lock) {
            activeJob?.cancel()
            activeJob = null
            pendingSinceMillis = 0L
        }
        scope.launch(NonCancellable + Dispatchers.Main.immediate) { overlay.close() }
        synchronized(lock) {
            (analyzer as? Closeable)?.close()
            analyzer = null
        }
        decisionCache.clear()
    }

    private suspend fun analyzeAfterSettle(
        windowId: Int,
        eventAt: Long,
        settleMillis: Long,
    ) {
        delay(settleMillis)
        synchronized(lock) { pendingSinceMillis = 0L }
        val window = withContext(Dispatchers.Main.immediate) { findChromeWindow(windowId) } ?: return
        val candidates = withContext(Dispatchers.Main.immediate) { collectCandidates(window) }
        val metrics = service.resources.displayMetrics
        val minimumEdge = (MinimumRegionDp * metrics.density).toInt().coerceAtLeast(1)
        val provisional =
            ChromeVisualRegionPlanner.fromNodes(
                candidates = candidates,
                windowWidth = metrics.widthPixels,
                windowHeight = metrics.heightPixels,
                minimumEdge = minimumEdge,
            )
        withContext(Dispatchers.Main.immediate) {
            provisional.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
            overlay.retain(provisional.mapTo(mutableSetOf(), ChromeVisualRegion::id))
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
                return
            }
        try {
            val topInset = (FallbackTopInsetDp * metrics.density).toInt()
            val fallback = ChromeVisualRegionPlanner.fallbackTiles(frame.width, frame.height, topInset)
            val regions = (provisional + fallback).distinctBy(ChromeVisualRegion::id)
            if (regions.isEmpty()) {
                withContext(Dispatchers.Main.immediate) { overlay.retain(emptySet()) }
                log(window.id, frame, startedAt, 0, 0, "no_region")
                return
            }
            withContext(Dispatchers.Main.immediate) {
                fallback.forEach { overlay.show(it, ChromeVisualOverlayState.Pending) }
                overlay.retain(regions.mapTo(mutableSetOf(), ChromeVisualRegion::id))
            }
            val captureIdentity = synchronized(lock) { identityGate.nextCapture() }
            var blocked = 0
            var allowed = 0
            for (region in regions) {
                val signature = signature(frame.bitmap, region) ?: continue
                val identity =
                    ChromeVisualIdentity(
                        windowId = window.id,
                        contentEpoch = captureIdentity.first,
                        captureSequence = captureIdentity.second,
                        regionId = region.id,
                        visualSignature = signature,
                    )
                val action =
                    decisionCache[signature] ?: analyze(frame.bitmap, region).also {
                        decisionCache[signature] = it
                    }
                if (!synchronized(lock) { identityGate.isCurrent(identity) }) return
                withContext(Dispatchers.Main.immediate) {
                    when (action) {
                        GloshiaVisualAction.Allow -> overlay.remove(region.id)
                        GloshiaVisualAction.Block ->
                            overlay.show(region, ChromeVisualOverlayState.Blocked)
                    }
                }
                if (action == GloshiaVisualAction.Block) blocked++ else allowed++
            }
            log(window.id, frame, startedAt, allowed, blocked, "success")
        } finally {
            frame.close()
        }
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

    private fun findChromeWindow(requestedWindowId: Int): AccessibilityWindowInfo? {
        val candidates =
            service.windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == ChromePackageName
            }
        return candidates.firstOrNull { it.id == requestedWindowId }
            ?: candidates.firstOrNull { it.isActive }
            ?: candidates.firstOrNull { it.isFocused }
    }

    private fun signature(
        bitmap: Bitmap,
        region: ChromeVisualRegion,
    ): Long? {
        if (region.width <= 0 || region.height <= 0) return null
        var hash = FnvOffsetBasis
        hash = (hash xor region.left.toLong()) * FnvPrime
        hash = (hash xor region.top.toLong()) * FnvPrime
        hash = (hash xor region.width.toLong()) * FnvPrime
        hash = (hash xor region.height.toLong()) * FnvPrime
        repeat(SignatureRows) { row ->
            val y = region.top + ((row + 0.5) * region.height / SignatureRows).toInt()
            repeat(SignatureColumns) { column ->
                val x = region.left + ((column + 0.5) * region.width / SignatureColumns).toInt()
                hash = (hash xor bitmap.getPixel(x, y).toLong()) * FnvPrime
            }
        }
        return hash
    }

    private fun fallbackTilesForDisplay(): List<ChromeVisualRegion> {
        val metrics = service.resources.displayMetrics
        return ChromeVisualRegionPlanner.fallbackTiles(
            metrics.widthPixels,
            metrics.heightPixels,
            (FallbackTopInsetDp * metrics.density).toInt(),
        )
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

    private class VisualDecisionCache(
        private val maximumSize: Int,
    ) {
        private val entries =
            object : LinkedHashMap<Long, GloshiaVisualAction>(maximumSize, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GloshiaVisualAction>?): Boolean =
                    size > maximumSize
            }

        operator fun get(signature: Long): GloshiaVisualAction? = synchronized(entries) { entries[signature] }

        operator fun set(
            signature: Long,
            action: GloshiaVisualAction,
        ) {
            synchronized(entries) { entries[signature] = action }
        }

        fun clear() = synchronized(entries) { entries.clear() }
    }

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val ContentSettleMillis = 150L
        const val MaximumSettleMillis = 500L
        const val MinimumRegionDp = 48
        const val FallbackTopInsetDp = 96
        const val MaxAccessibilityNodes = 400
        const val MaxDecisionCacheEntries = 128
        const val SignatureColumns = 12
        const val SignatureRows = 8
        const val FnvOffsetBasis = -3750763034362895579L
        const val FnvPrime = 1099511628211L
        const val LogTag = "ChromeVisual"
    }
}
