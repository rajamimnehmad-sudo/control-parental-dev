package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualRegion(
    val id: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()
}

internal data class ChromeVisualIdentity(
    val windowId: Int,
    val contentEpoch: Long,
    val captureSequence: Long,
    val regionId: String,
    val visualSignature: Long,
)

internal class ChromeVisualIdentityGate {
    private var windowId = InvalidWindowId
    private var contentEpoch = 0L
    private var captureSequence = 0L

    fun beginWindow(windowId: Int): Long {
        if (this.windowId != windowId) {
            this.windowId = windowId
            contentEpoch += 1
        }
        return contentEpoch
    }

    fun invalidate(windowId: Int): Long {
        this.windowId = windowId
        contentEpoch += 1
        return contentEpoch
    }

    fun nextCapture(): Pair<Long, Long> {
        captureSequence += 1
        return contentEpoch to captureSequence
    }

    fun isCurrent(identity: ChromeVisualIdentity): Boolean =
        identity.windowId == windowId &&
            identity.contentEpoch == contentEpoch &&
            identity.captureSequence == captureSequence

    private companion object {
        const val InvalidWindowId = -1
    }
}

internal data class ChromeVisualNodeCandidate(
    val className: String,
    val hasDescription: Boolean,
    val childCount: Int,
    val region: ChromeVisualRegion,
)

internal object ChromeVisualRegionPlanner {
    fun fromNodes(
        candidates: List<ChromeVisualNodeCandidate>,
        windowWidth: Int,
        windowHeight: Int,
        minimumEdge: Int,
    ): List<ChromeVisualRegion> =
        candidates
            .asSequence()
            .filter { candidate ->
                val className = candidate.className.lowercase()
                val imageRole = "image" in className || "photo" in className
                val describedLeaf = candidate.hasDescription && candidate.childCount == 0
                val maximumAreaRatio = if (imageRole) MaximumImageAreaRatio else MaximumLeafAreaRatio
                (imageRole || describedLeaf) &&
                    candidate.region.width >= minimumEdge &&
                    candidate.region.height >= minimumEdge &&
                    candidate.region.top >= windowHeight / 12 &&
                    candidate.region.area <= windowWidth.toLong() * windowHeight * maximumAreaRatio
            }
            .mapNotNull { clamp(it.region, windowWidth, windowHeight) }
            .sortedByDescending(ChromeVisualRegion::area)
            .fold(mutableListOf<ChromeVisualRegion>()) { accepted, region ->
                if (accepted.none { overlapRatio(it, region) >= MaximumDuplicateOverlap }) {
                    accepted += region
                }
                accepted
            }
            .take(MaxNodeRegions)
            .toList()

    fun changedFallbackTiles(
        windowWidth: Int,
        windowHeight: Int,
        topInset: Int,
        previousSignatures: Map<String, Long>,
        currentSignatures: Map<String, Long>,
    ): List<ChromeVisualRegion> {
        if (previousSignatures.isEmpty()) return fallbackTiles(windowWidth, windowHeight, topInset)
        return fallbackTiles(windowWidth, windowHeight, topInset)
            .filter { previousSignatures[it.id] != currentSignatures[it.id] }
            .take(MaxChangedFallbackRegions)
    }

    fun fallbackTiles(
        windowWidth: Int,
        windowHeight: Int,
        topInset: Int,
    ): List<ChromeVisualRegion> {
        if (windowWidth <= 0 || windowHeight <= topInset) return emptyList()
        val contentHeight = windowHeight - topInset
        val tileHeight = (contentHeight / FallbackRows).coerceAtLeast(1)
        val tileWidth = (windowWidth / FallbackColumns).coerceAtLeast(1)
        return buildList {
            repeat(FallbackRows) { row ->
                val top = topInset + row * tileHeight
                val bottom = if (row == FallbackRows - 1) windowHeight else top + tileHeight
                repeat(FallbackColumns) { column ->
                    val left = column * tileWidth
                    val right = if (column == FallbackColumns - 1) windowWidth else left + tileWidth
                    add(ChromeVisualRegion("tile_${row}_$column", left, top, right, bottom))
                }
            }
        }
    }

    private fun clamp(
        region: ChromeVisualRegion,
        width: Int,
        height: Int,
    ): ChromeVisualRegion? {
        val clamped =
            region.copy(
                left = region.left.coerceIn(0, width),
                top = region.top.coerceIn(0, height),
                right = region.right.coerceIn(0, width),
                bottom = region.bottom.coerceIn(0, height),
            )
        return clamped.takeIf { it.width > 0 && it.height > 0 }
    }

    private fun overlapRatio(
        first: ChromeVisualRegion,
        second: ChromeVisualRegion,
    ): Double {
        val width = (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0)
        val height = (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)).coerceAtLeast(0)
        val intersection = width.toLong() * height.toLong()
        return intersection.toDouble() / minOf(first.area, second.area).coerceAtLeast(1L)
    }

    private const val MaxNodeRegions = 12
    private const val MaxChangedFallbackRegions = 4
    private const val FallbackRows = 4
    private const val FallbackColumns = 2
    private const val MaximumDuplicateOverlap = 0.85
    private const val MaximumImageAreaRatio = 0.80
    private const val MaximumLeafAreaRatio = 0.35
}
