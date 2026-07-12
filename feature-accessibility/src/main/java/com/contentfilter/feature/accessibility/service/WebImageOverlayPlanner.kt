package com.contentfilter.feature.accessibility.service

data class OverlayBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = (right - left).coerceAtLeast(0)

    val height: Int
        get() = (bottom - top).coerceAtLeast(0)

    val centerY: Int
        get() = top + height / 2

    fun intersect(other: OverlayBounds): OverlayBounds? {
        val intersection =
            OverlayBounds(
                left = maxOf(left, other.left),
                top = maxOf(top, other.top),
                right = minOf(right, other.right),
                bottom = minOf(bottom, other.bottom),
            )
        return intersection.takeIf { it.width > 1 && it.height > 1 }
    }
}

data class WebImageOverlayPlan(
    val coverage: WebImageOverlayCoverage,
    val bounds: List<OverlayBounds>,
) {
    companion object {
        val None = WebImageOverlayPlan(WebImageOverlayCoverage.None, emptyList())
    }
}

enum class WebImageOverlayCoverage {
    None,
    Regions,
    FullContent,
}

object WebImageOverlayPlanner {
    fun plan(
        imagesBlocked: Boolean,
        rootBounds: OverlayBounds?,
        addressBarBounds: OverlayBounds?,
        visualBounds: List<OverlayBounds>,
        individualCoverageReliable: Boolean,
        fallbackTopInsetPx: Int,
        fallbackBottomInsetPx: Int,
    ): WebImageOverlayPlan {
        if (!imagesBlocked || rootBounds == null) return WebImageOverlayPlan.None
        val contentBounds =
            contentBounds(
                root = rootBounds,
                addressBar = addressBarBounds,
                fallbackTopInsetPx = fallbackTopInsetPx,
                fallbackBottomInsetPx = fallbackBottomInsetPx,
            ) ?: return WebImageOverlayPlan.None
        val clippedVisuals =
            visualBounds
                .mapNotNull(contentBounds::intersect)
                .filter { it.width >= MinimumVisualSizePx && it.height >= MinimumVisualSizePx }
                .distinct()
        return if (individualCoverageReliable && clippedVisuals.isNotEmpty()) {
            WebImageOverlayPlan(WebImageOverlayCoverage.Regions, clippedVisuals)
        } else {
            WebImageOverlayPlan(WebImageOverlayCoverage.FullContent, listOf(contentBounds))
        }
    }

    private fun contentBounds(
        root: OverlayBounds,
        addressBar: OverlayBounds?,
        fallbackTopInsetPx: Int,
        fallbackBottomInsetPx: Int,
    ): OverlayBounds? {
        val candidate =
            when {
                addressBar == null ->
                    root.copy(
                        top = root.top + fallbackTopInsetPx,
                        bottom = root.bottom - fallbackBottomInsetPx,
                    )
                addressBar.centerY <= root.centerY ->
                    root.copy(
                        top = maxOf(root.top, addressBar.bottom),
                        bottom = root.bottom - fallbackBottomInsetPx,
                    )
                else ->
                    root.copy(
                        top = root.top + fallbackTopInsetPx,
                        bottom = minOf(root.bottom, addressBar.top),
                    )
            }
        return candidate.takeIf { it.width > 1 && it.height > 1 }
    }

    private const val MinimumVisualSizePx = 8
}

object WebVisualNodeClassifier {
    fun isVisualNode(
        className: String?,
        viewId: String?,
    ): Boolean {
        val normalizedClass = className.orEmpty().lowercase()
        val normalizedId = viewId.orEmpty().lowercase()
        return VisualClassParts.any(normalizedClass::contains) || VisualIdParts.any(normalizedId::contains)
    }

    private val VisualClassParts = setOf("imageview", "imagebutton", "photo", "picture")
    private val VisualIdParts =
        setOf(
            "image",
            "photo",
            "picture",
            "thumbnail",
            "avatar",
            "logo",
            "banner",
            "carousel",
            "gallery",
            "preview",
            "video",
            "map",
            "media",
        )
}
