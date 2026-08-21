package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent

internal object ChromeVisualAtomicMutationPolicy {
    fun requiresReplay(event: AccessibilityEvent): Boolean =
        requiresReplay(
            eventType = event.eventType,
            contentChangeTypes = event.contentChangeTypes,
        )

    fun requiresReplay(
        eventType: Int,
        contentChangeTypes: Int,
    ): Boolean {
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) return true
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
        if (contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) return true
        return contentChangeTypes and VisualContentChangeMask != 0
    }

    fun requiresGeometryRestart(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

    fun shouldCoalesceIntoActiveAnalysis(
        replayRequired: Boolean,
        geometryRestart: Boolean,
        analysisActive: Boolean,
    ): Boolean = replayRequired && !geometryRestart && analysisActive

    private val VisualContentChangeMask =
        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
}

internal class ChromeVisualAtomicReplayCoordinator {
    private var nextRevision = 0L
    private var activeRevision: Long? = null

    @Synchronized
    fun request(): Long {
        nextRevision += 1L
        activeRevision = nextRevision
        return nextRevision
    }

    @Synchronized
    fun currentRevision(): Long? = activeRevision

    @Synchronized
    fun complete(revision: Long): Boolean {
        if (activeRevision != revision) return false
        activeRevision = null
        return true
    }

    @Synchronized
    fun clear() {
        nextRevision = 0L
        activeRevision = null
    }
}

internal object ChromeVisualReplayRegionPolicy {
    fun select(
        replayActive: Boolean,
        fallbackTiles: List<ChromeVisualRegion>,
        visuallyChanged: List<ChromeVisualRegion>,
        confirmations: List<ChromeVisualRegion>,
    ): List<ChromeVisualRegion> =
        ((if (replayActive) fallbackTiles else visuallyChanged) + confirmations)
            .distinctBy(ChromeVisualRegion::id)
}
