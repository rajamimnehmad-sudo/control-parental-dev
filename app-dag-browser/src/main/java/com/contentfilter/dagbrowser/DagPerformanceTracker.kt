package com.contentfilter.dagbrowser

internal enum class DagPerformanceMetric(
    val wireValue: String,
) {
    PageLoadStarted("page_load_started"),
    PageVisible("page_visible"),
    PageAnalysisReady("page_analysis_ready"),
    ViewportImagesReady("viewport_images_ready"),
}

internal data class DagPerformanceEvent(
    val navigationId: Int,
    val metric: DagPerformanceMetric,
    val elapsedMillis: Long,
)

/**
 * Keeps performance signals monotonic and unique for one top-level navigation.
 */
internal class DagPerformanceTracker(
    private val elapsedRealtime: () -> Long,
) {
    private var navigationId = 0
    private var startedAtMillis: Long? = null
    private val emittedMetrics = mutableSetOf<DagPerformanceMetric>()

    @Synchronized
    fun begin(): DagPerformanceEvent {
        navigationId += 1
        startedAtMillis = elapsedRealtime()
        emittedMetrics.clear()
        emittedMetrics += DagPerformanceMetric.PageLoadStarted
        return DagPerformanceEvent(
            navigationId = navigationId,
            metric = DagPerformanceMetric.PageLoadStarted,
            elapsedMillis = 0,
        )
    }

    @Synchronized
    fun mark(metric: DagPerformanceMetric): DagPerformanceEvent? {
        if (metric == DagPerformanceMetric.PageLoadStarted) return null
        val startedAt = startedAtMillis ?: return null
        if (!emittedMetrics.add(metric)) return null
        return DagPerformanceEvent(
            navigationId = navigationId,
            metric = metric,
            elapsedMillis = (elapsedRealtime() - startedAt).coerceAtLeast(0),
        )
    }
}
