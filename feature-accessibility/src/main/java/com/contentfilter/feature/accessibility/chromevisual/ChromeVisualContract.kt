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

internal data class ChromeVisualViewport(
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
        viewport: ChromeVisualViewport,
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
                    candidate.region.top >= viewport.top + viewport.height / 12 &&
                    candidate.region.area <= viewport.area * maximumAreaRatio
            }
            .mapNotNull { clamp(it.region, viewport) }
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
        viewport: ChromeVisualViewport,
        topInset: Int,
        previousSignatures: Map<String, Long>,
        currentSignatures: Map<String, Long>,
    ): List<ChromeVisualRegion> {
        if (previousSignatures.isEmpty()) return fallbackTiles(viewport, topInset)
        return fallbackTiles(viewport, topInset)
            .filter { previousSignatures[it.id] != currentSignatures[it.id] }
            .take(MaxChangedFallbackRegions)
    }

    fun fallbackTiles(
        viewport: ChromeVisualViewport,
        topInset: Int,
    ): List<ChromeVisualRegion> {
        if (viewport.width <= 0 || viewport.height <= topInset) return emptyList()
        val contentTop = viewport.top + topInset
        val contentHeight = viewport.bottom - contentTop
        val tileHeight = (contentHeight / FallbackRows).coerceAtLeast(1)
        val tileWidth = (viewport.width / FallbackColumns).coerceAtLeast(1)
        return buildList {
            repeat(FallbackRows) { row ->
                val top = contentTop + row * tileHeight
                val bottom = if (row == FallbackRows - 1) viewport.bottom else top + tileHeight
                repeat(FallbackColumns) { column ->
                    val left = viewport.left + column * tileWidth
                    val right = if (column == FallbackColumns - 1) viewport.right else left + tileWidth
                    add(ChromeVisualRegion("tile_${row}_$column", left, top, right, bottom))
                }
            }
        }
    }

    private fun clamp(
        region: ChromeVisualRegion,
        viewport: ChromeVisualViewport,
    ): ChromeVisualRegion? {
        val clamped =
            region.copy(
                left = region.left.coerceIn(viewport.left, viewport.right),
                top = region.top.coerceIn(viewport.top, viewport.bottom),
                right = region.right.coerceIn(viewport.left, viewport.right),
                bottom = region.bottom.coerceIn(viewport.top, viewport.bottom),
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

internal object ChromeVisualGeometryMapper {
    fun toFrame(
        region: ChromeVisualRegion,
        viewport: ChromeVisualViewport,
        frameWidth: Int,
        frameHeight: Int,
    ): ChromeVisualRegion? {
        if (viewport.width <= 0 || viewport.height <= 0 || frameWidth <= 0 || frameHeight <= 0) return null
        val left = scale(region.left - viewport.left, viewport.width, frameWidth)
        val top = scale(region.top - viewport.top, viewport.height, frameHeight)
        val right = scale(region.right - viewport.left, viewport.width, frameWidth)
        val bottom = scale(region.bottom - viewport.top, viewport.height, frameHeight)
        return ChromeVisualRegion(
            id = region.id,
            left = left.coerceIn(0, frameWidth),
            top = top.coerceIn(0, frameHeight),
            right = right.coerceIn(0, frameWidth),
            bottom = bottom.coerceIn(0, frameHeight),
        ).takeIf { it.width > 0 && it.height > 0 }
    }

    private fun scale(
        value: Int,
        sourceSize: Int,
        targetSize: Int,
    ): Int = (value.toLong() * targetSize / sourceSize).toInt()
}

internal object ChromeVisualSignatureLedger {
    fun advance(
        previous: Map<String, Long>,
        current: Map<String, Long>,
        processedRegionIds: Set<String>,
    ): Map<String, Long> =
        current.mapValues { (regionId, signature) ->
            when {
                previous[regionId] == signature -> signature
                regionId in processedRegionIds -> signature
                else -> previous[regionId] ?: signature
            }
        }
}
