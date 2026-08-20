package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualDecision
import java.util.LinkedHashMap

internal data class RegionCounts(
    val allowed: Int,
    val blocked: Int,
)

internal data class ChromeVisualBaselineContext(
    val windowId: Int,
    val pageIdentity: Long,
    val viewport: ChromeVisualViewport,
)

internal class ChromeVisualBaselineCoordinator {
    private var active: ChromeVisualBaselineContext? = null
    private var rescanRequested = false

    @Synchronized
    fun coalesceIfActive(context: ChromeVisualBaselineContext): Boolean {
        if (active != context) return false
        rescanRequested = true
        return true
    }

    @Synchronized
    fun replace(context: ChromeVisualBaselineContext) {
        active = context
        rescanRequested = false
    }

    @Synchronized
    fun startIfIdle(context: ChromeVisualBaselineContext): Boolean {
        if (active != null) return false
        replace(context)
        return true
    }

    @Synchronized
    fun finish(context: ChromeVisualBaselineContext): Boolean {
        if (active != context) return false
        active = null
        return rescanRequested.also { rescanRequested = false }
    }

    @Synchronized
    fun clear() {
        active = null
        rescanRequested = false
    }
}

internal object ChromeVisualEventModePolicy {
    fun requiresBaseline(
        pageChanged: Boolean,
        activeWindowId: Int,
        eventWindowId: Int,
        lastViewport: ChromeVisualViewport?,
        eventViewport: ChromeVisualViewport,
        hasSignatures: Boolean,
    ): Boolean =
        pageChanged ||
            activeWindowId != eventWindowId ||
            lastViewport != eventViewport ||
            !hasSignatures
}

internal object ChromeVisualVerificationSchedule {
    fun delayMillis(
        stableVerificationCount: Int,
        hasDynamicRegions: Boolean = false,
    ): Long =
        if (hasDynamicRegions || stableVerificationCount < FastVerificationCount) {
            FastDelayMillis
        } else {
            StableDelayMillis
        }

    private const val FastVerificationCount = 2
    private const val FastDelayMillis = 500L
    private const val StableDelayMillis = 1_000L
}

internal class ChromeVisualPageBlockLedger {
    private var pageIdentity: Long? = null
    private val blockedTileIds = mutableSetOf<String>()

    @Synchronized
    fun beginPage(identity: Long): Boolean {
        if (pageIdentity == identity) return false
        pageIdentity = identity
        blockedTileIds.clear()
        return true
    }

    @Synchronized
    fun recordBlocked(
        identity: Long,
        blockedRegion: ChromeVisualRegion,
        fallbackTiles: List<ChromeVisualRegion>,
    ) {
        if (pageIdentity != identity) return
        fallbackTiles
            .filter { overlapArea(it, blockedRegion) > 0L }
            .mapTo(blockedTileIds, ChromeVisualRegion::id)
    }

    @Synchronized
    fun mustRemainBlocked(
        identity: Long,
        regionId: String,
    ): Boolean = pageIdentity == identity && regionId in blockedTileIds

    @Synchronized
    fun clear() {
        pageIdentity = null
        blockedTileIds.clear()
    }

    private fun overlapArea(
        first: ChromeVisualRegion,
        second: ChromeVisualRegion,
    ): Long {
        val width = (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0)
        val height = (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)).coerceAtLeast(0)
        return width.toLong() * height
    }
}

internal class ChromeVisualDecisionCache(
    private val maximumSize: Int,
) {
    private val entries =
        object : LinkedHashMap<Long, GloshiaVisualDecision>(maximumSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GloshiaVisualDecision>?): Boolean =
                size > maximumSize
        }

    operator fun get(signature: Long): GloshiaVisualDecision? = synchronized(entries) { entries[signature] }

    operator fun set(
        signature: Long,
        decision: GloshiaVisualDecision,
    ) {
        synchronized(entries) { entries[signature] = decision }
    }

    fun clear() = synchronized(entries) { entries.clear() }
}
