package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.abs

internal enum class ChromeVisualShieldRasterRootCause {
    EPOCH_MISMATCH,
    PROTECTED_SURFACE_CAPTURED,
    CANVAS_PRE_DRAW,
    MAPPING_SHIFT,
    EXPECTED_CONTENT_PRESENT,
    UNKNOWN,
}

internal data class ChromeVisualShieldRasterBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width.coerceAtLeast(0) * height.coerceAtLeast(0)
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    fun isValid(): Boolean = width > 0 && height > 0

    fun intersectionArea(other: ChromeVisualShieldRasterBox): Int =
        (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0) *
            (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0)
}

internal data class ChromeVisualShieldRasterColorEvidence(
    val name: String,
    val count: Int,
    val fraction: Double,
    val bounds: ChromeVisualShieldRasterBox?,
)

internal data class ChromeVisualShieldRasterPointSample(
    val label: String,
    val x: Int,
    val y: Int,
    val rgbHex: String,
)

internal data class ChromeVisualShieldRasterFingerprint(
    val width: Int,
    val height: Int,
    val colors: List<ChromeVisualShieldRasterColorEvidence>,
    val cardClusters: List<ChromeVisualShieldRasterBox>,
    val samples: List<ChromeVisualShieldRasterPointSample>,
) {
    fun color(name: String): ChromeVisualShieldRasterColorEvidence =
        checkNotNull(colors.firstOrNull { it.name == name })
}

internal data class ChromeVisualShieldRasterMappingDelta(
    val deltaX: Double,
    val deltaY: Double,
    val scaleX: Double,
    val scaleY: Double,
)

internal data class ChromeVisualShieldRasterProvenanceSignals(
    val attestedContentEpoch: Long?,
    val attestedRegionSequence: Long?,
    val captureContentEpoch: Long?,
    val captureRegionSequence: Long?,
    val fullFrame: ChromeVisualShieldRasterFingerprint?,
    val crop: ChromeVisualShieldRasterFingerprint?,
    val carrierAlignedWithSearchEnvelope: Boolean,
    val expectedCardPresentInCrop: Boolean,
    val expectedDrawNonNeutralFraction: Double,
    val matchingCardOutsideSearchEnvelope: Boolean,
    val mappingDelta: ChromeVisualShieldRasterMappingDelta?,
)

internal data class ChromeVisualShieldRasterProvenanceClassification(
    val cause: ChromeVisualShieldRasterRootCause,
    val basis: String,
    val mappingDelta: ChromeVisualShieldRasterMappingDelta? = null,
)

/** Pure fail-closed causal classifier. It has no access to planner, inference, or release state. */
internal object ChromeVisualShieldRasterProvenanceClassifier {
    fun classify(
        signals: ChromeVisualShieldRasterProvenanceSignals,
    ): ChromeVisualShieldRasterProvenanceClassification {
        if (
            signals.attestedContentEpoch == null ||
            signals.attestedRegionSequence == null ||
            signals.captureContentEpoch == null ||
            signals.captureRegionSequence == null
        ) {
            return unknown("identity_evidence_incomplete")
        }
        if (
            signals.attestedContentEpoch != signals.captureContentEpoch ||
            signals.attestedRegionSequence != signals.captureRegionSequence
        ) {
            return ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.EPOCH_MISMATCH,
                "attested=${signals.attestedContentEpoch}:${signals.attestedRegionSequence}," +
                    "capture=${signals.captureContentEpoch}:${signals.captureRegionSequence}",
            )
        }

        val full = signals.fullFrame ?: return unknown("full_frame_fingerprint_missing")
        val crop = signals.crop ?: return unknown("crop_fingerprint_missing")
        val markerCount = crop.color(SurfaceMarker).count
        val expectedContent =
            signals.expectedCardPresentInCrop &&
                signals.expectedDrawNonNeutralFraction >= MinimumDrawEvidenceFraction
        if (markerCount >= MinimumMarkerPixels && expectedContent) {
            return unknown("surface_marker_and_expected_content_conflict")
        }
        if (markerCount >= MinimumMarkerPixels && !expectedContent) {
            return ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.PROTECTED_SURFACE_CAPTURED,
                "surfaceMarkerPixels=$markerCount expectedCarrierCompatible=false",
            )
        }

        val canvas = crop.color(CanvasNeutral)
        val card = crop.color(Card)
        if (
            signals.carrierAlignedWithSearchEnvelope &&
            canvas.fraction >= MinimumNeutralDominance &&
            card.count < MinimumCardPixels &&
            signals.expectedDrawNonNeutralFraction <= MaximumPreDrawForeignFraction
        ) {
            return ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.CANVAS_PRE_DRAW,
                "canvasNeutralFraction=${canvas.fraction} cardPixels=${card.count} " +
                    "drawForeignFraction=${signals.expectedDrawNonNeutralFraction}",
            )
        }
        if (signals.matchingCardOutsideSearchEnvelope && !expectedContent) {
            val delta = signals.mappingDelta ?: return unknown("mapping_candidate_without_delta")
            return ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.MAPPING_SHIFT,
                "delta=${delta.deltaX},${delta.deltaY} scale=${delta.scaleX},${delta.scaleY}",
                delta,
            )
        }
        if (expectedContent) {
            return ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.EXPECTED_CONTENT_PRESENT,
                "cardPixels=${card.count} drawForeignFraction=${signals.expectedDrawNonNeutralFraction}",
            )
        }
        return unknown(
            "no_unique_signature fullCard=${full.color(Card).count} cropCard=${card.count} " +
                "canvasNeutralFraction=${canvas.fraction} drawForeignFraction=${signals.expectedDrawNonNeutralFraction}",
        )
    }

    private fun unknown(basis: String) =
        ChromeVisualShieldRasterProvenanceClassification(
            ChromeVisualShieldRasterRootCause.UNKNOWN,
            basis,
        )

    internal const val CanvasNeutral = "canvas_neutral"
    internal const val Card = "card"
    internal const val Body = "body"
    internal const val SurfaceNeutral = "surface_neutral"
    internal const val SurfaceMarker = "surface_marker"
    private const val MinimumMarkerPixels = 16
    private const val MinimumCardPixels = 16
    private const val MinimumDrawEvidenceFraction = 0.05
    private const val MinimumNeutralDominance = 0.98
    private const val MaximumPreDrawForeignFraction = 0.01
}

/** Reads only the supplied snapshot and returns aggregate evidence; it never mutates input pixels. */
internal object ChromeVisualShieldRasterFingerprintFactory {
    private data class KnownColor(
        val name: String,
        val rgb: Int,
    )

    private val knownColors =
        listOf(
            KnownColor(ChromeVisualShieldRasterProvenanceClassifier.CanvasNeutral, 0x202428),
            KnownColor(ChromeVisualShieldRasterProvenanceClassifier.Card, 0xf5f5f5),
            KnownColor(ChromeVisualShieldRasterProvenanceClassifier.Body, 0x111111),
            KnownColor(ChromeVisualShieldRasterProvenanceClassifier.SurfaceNeutral, 0x202124),
            KnownColor(ChromeVisualShieldRasterProvenanceClassifier.SurfaceMarker, 0x00c8ff),
        )

    fun create(
        width: Int,
        height: Int,
        argb: IntArray,
        samplePoints: List<Pair<String, Pair<Int, Int>>> = emptyList(),
    ): ChromeVisualShieldRasterFingerprint {
        require(width > 0 && height > 0 && argb.size == width * height)
        val colors =
            knownColors.map { known ->
                var count = 0
                var left = width
                var top = height
                var right = -1
                var bottom = -1
                argb.forEachIndexed { index, pixel ->
                    if (!near(pixel, known.rgb)) return@forEachIndexed
                    val x = index % width
                    val y = index / width
                    count += 1
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
                ChromeVisualShieldRasterColorEvidence(
                    name = known.name,
                    count = count,
                    fraction = count.toDouble() / argb.size,
                    bounds =
                        if (count == 0) {
                            null
                        } else {
                            ChromeVisualShieldRasterBox(left, top, right + 1, bottom + 1)
                        },
                )
            }
        val samples =
            samplePoints.mapNotNull { (label, point) ->
                val (x, y) = point
                if (x !in 0 until width || y !in 0 until height) return@mapNotNull null
                ChromeVisualShieldRasterPointSample(label, x, y, rgbHex(argb[y * width + x]))
            }
        return ChromeVisualShieldRasterFingerprint(
            width = width,
            height = height,
            colors = colors,
            cardClusters = colorClusters(width, height, argb, 0xf5f5f5),
            samples = samples,
        )
    }

    fun foreignFraction(
        width: Int,
        height: Int,
        argb: IntArray,
        region: ChromeVisualShieldRasterBox?,
        referenceRgb: Int = 0x202428,
    ): Double {
        val bounded = region?.bounded(width, height) ?: return 0.0
        if (!bounded.isValid()) return 0.0
        var foreign = 0
        for (y in bounded.top until bounded.bottom) {
            for (x in bounded.left until bounded.right) {
                if (!near(argb[y * width + x], referenceRgb)) foreign += 1
            }
        }
        return foreign.toDouble() / bounded.area
    }

    private fun colorClusters(
        width: Int,
        height: Int,
        argb: IntArray,
        rgb: Int,
    ): List<ChromeVisualShieldRasterBox> {
        val matching = BooleanArray(argb.size) { near(argb[it], rgb) }
        val visited = BooleanArray(argb.size)
        val queue = IntArray(argb.size)
        val output = mutableListOf<ChromeVisualShieldRasterBox>()
        for (seed in argb.indices) {
            if (!matching[seed] || visited[seed]) continue
            var head = 0
            var tail = 0
            var left = seed % width
            var right = left
            var top = seed / width
            var bottom = top
            var count = 0
            visited[seed] = true
            queue[tail++] = seed
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                count += 1

                fun visit(candidate: Int) {
                    if (!matching[candidate] || visited[candidate]) return
                    visited[candidate] = true
                    queue[tail++] = candidate
                }
                if (x > 0) visit(index - 1)
                if (x + 1 < width) visit(index + 1)
                if (y > 0) visit(index - width)
                if (y + 1 < height) visit(index + width)
            }
            if (count >= MinimumClusterPixels) {
                output += ChromeVisualShieldRasterBox(left, top, right + 1, bottom + 1)
            }
        }
        return output.sortedWith(compareBy({ it.top }, { it.left }, { it.bottom }, { it.right }))
    }

    private fun ChromeVisualShieldRasterBox.bounded(
        width: Int,
        height: Int,
    ) = ChromeVisualShieldRasterBox(
        left.coerceIn(0, width),
        top.coerceIn(0, height),
        right.coerceIn(0, width),
        bottom.coerceIn(0, height),
    )

    private fun near(
        argb: Int,
        rgb: Int,
    ): Boolean =
        abs((argb ushr 16 and 0xff) - (rgb ushr 16 and 0xff)) <= ColorTolerance &&
            abs((argb ushr 8 and 0xff) - (rgb ushr 8 and 0xff)) <= ColorTolerance &&
            abs((argb and 0xff) - (rgb and 0xff)) <= ColorTolerance

    private fun rgbHex(argb: Int): String = "%06x".format(argb and 0xffffff)

    private const val ColorTolerance = 2
    private const val MinimumClusterPixels = 8
}
