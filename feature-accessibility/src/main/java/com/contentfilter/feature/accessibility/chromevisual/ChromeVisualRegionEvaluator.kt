package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ChromeVisualRegionEvaluator(
    private val service: AccessibilityService,
    private val decisionCache: ChromeVisualDecisionCache,
    private val regionAnalyzer: ChromeVisualRegionAnalyzer,
    private val presentation: ChromeVisualRegionPresentation,
) {
    suspend fun evaluate(
        windowId: Int,
        pageIdentity: Long,
        frame: ChromeWindowFrame,
        viewport: ChromeVisualViewport,
        regions: List<ChromeVisualRegion>,
        observedChanges: Set<String>,
    ): RegionCounts {
        val captureIdentity = presentation.nextCaptureIdentity()
        var blocked = 0
        var allowed = 0
        var completed = true
        val processedRegionIds = mutableSetOf<String>()
        val fallbackTiles = fallbackTiles(viewport)

        for (region in regions) {
            val temporal = region.id.startsWith(FallbackRegionPrefix)
            val videoKey = ChromeVisualVideoRegionKey(windowId, pageIdentity, region.id)
            if (temporal) {
                withContext(Dispatchers.Main.immediate) {
                    presentation.coverTemporalIfNeeded(
                        videoKey = videoKey,
                        region = region,
                        visuallyChanged = region.id in observedChanges,
                    )
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
                    presentation.applyIfCurrent(
                        identity = identity,
                        pageIdentity = pageIdentity,
                        viewport = viewport,
                        region = region,
                        temporal = temporal,
                        videoKey = videoKey,
                        decision = decision,
                        fallbackTiles = fallbackTiles,
                    )
                }
            if (!applied) {
                return RegionCounts(
                    allowed = allowed,
                    blocked = blocked,
                    completed = false,
                    processedRegionIds = processedRegionIds,
                )
            }
            processedRegionIds += region.id
            if (decision.action == GloshiaVisualAction.Block) blocked++ else allowed++
        }

        return RegionCounts(allowed, blocked, completed, processedRegionIds)
    }

    private fun fallbackTiles(viewport: ChromeVisualViewport): List<ChromeVisualRegion> =
        ChromeVisualRegionPlanner.fallbackTiles(
            viewport,
            (FallbackTopInsetDp * service.resources.displayMetrics.density).toInt(),
        )

    private companion object {
        const val FallbackTopInsetDp = 96
        const val FallbackRegionPrefix = "tile_"
    }
}

internal class ChromeVisualRegionPresentation(
    private val lock: Any,
    private val identityGate: ChromeVisualIdentityGate,
    private val windowInspector: ChromeVisualWindowInspector,
    private val overlay: ChromeVisualOverlay,
    private val videoPolicy: ChromeVisualVideoPolicy,
    private val pageBlockLedger: ChromeVisualPageBlockLedger,
) {
    fun nextCaptureIdentity(): Pair<Long, Long> = synchronized(lock) { identityGate.nextCapture() }

    fun coverTemporalIfNeeded(
        videoKey: ChromeVisualVideoRegionKey,
        region: ChromeVisualRegion,
        visuallyChanged: Boolean,
    ) {
        if (
            videoPolicy.beforeSample(videoKey, region, visuallyChanged) ==
            ChromeVisualPresentation.Covered
        ) {
            overlay.show(region, ChromeVisualOverlayState.Pending)
            clipForInputMethod()
        }
    }

    fun applyIfCurrent(
        identity: ChromeVisualIdentity,
        pageIdentity: Long,
        viewport: ChromeVisualViewport,
        region: ChromeVisualRegion,
        temporal: Boolean,
        videoKey: ChromeVisualVideoRegionKey,
        decision: GloshiaVisualDecision,
        fallbackTiles: List<ChromeVisualRegion>,
    ): Boolean {
        if (!windowStillCurrent(identity.windowId, pageIdentity, viewport)) return false
        if (!synchronized(lock) { identityGate.isCurrent(identity) }) return false

        if (temporal) {
            presentTemporal(videoKey, region, decision)
        } else {
            presentStatic(pageIdentity, region, decision, fallbackTiles)
        }
        clipForInputMethod()
        return true
    }

    private fun windowStillCurrent(
        windowId: Int,
        pageIdentity: Long,
        viewport: ChromeVisualViewport,
    ): Boolean =
        windowInspector.find(windowId)?.let {
            windowInspector.pageIdentity(it) == pageIdentity && windowInspector.viewport(it) == viewport
        } == true

    private fun presentTemporal(
        videoKey: ChromeVisualVideoRegionKey,
        region: ChromeVisualRegion,
        decision: GloshiaVisualDecision,
    ) {
        when (videoPolicy.record(videoKey, decision.toSampleDecision())) {
            ChromeVisualPresentation.Visible -> overlay.remove(region.id)
            ChromeVisualPresentation.Covered -> overlay.show(region, ChromeVisualOverlayState.Blocked)
        }
    }

    private fun presentStatic(
        pageIdentity: Long,
        region: ChromeVisualRegion,
        decision: GloshiaVisualDecision,
        fallbackTiles: List<ChromeVisualRegion>,
    ) {
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

    private fun clipForInputMethod() {
        windowInspector.inputMethodTop()?.let(overlay::clipBottom)
    }

    private fun GloshiaVisualDecision.toSampleDecision(): ChromeVisualSampleDecision =
        when {
            action == GloshiaVisualAction.Allow -> ChromeVisualSampleDecision.Allow
            reason == GloshiaVisualPolicyContract.ModelFilterReason -> ChromeVisualSampleDecision.Block
            else -> ChromeVisualSampleDecision.Unavailable
        }
}
