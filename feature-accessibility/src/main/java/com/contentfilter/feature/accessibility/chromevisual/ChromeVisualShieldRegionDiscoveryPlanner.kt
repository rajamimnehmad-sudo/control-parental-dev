package com.contentfilter.feature.accessibility.chromevisual

import java.security.MessageDigest
import kotlin.math.max

/**
 * Pure, bounded content-island discovery over the current protected capture envelope.
 * It retains no pixels and receives no fixture oracle, source identity, or expected verdict.
 */
internal class ChromeVisualShieldRegionDiscoveryPlanner(
    private val maximumRegions: Int = DefaultMaximumRegions,
) {
    fun discover(
        raster: ChromeVisualShieldDiscoveryRaster,
        identity: ChromeVisualShieldIdentity,
        discoverySequence: Long,
        carrierHints: List<ChromeVisualShieldCarrierHint> = emptyList(),
        isIdentityCurrent: () -> Boolean = { true },
        isCancelled: () -> Boolean = { false },
    ): ChromeVisualShieldRegionDiscoveryResult {
        if (!raster.isValid() || discoverySequence <= 0 || maximumRegions <= 0) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.InvalidSearchEnvelope)
        }
        if (!isIdentityCurrent()) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.StaleIdentity)
        }
        if (isCancelled()) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.Cancelled)
        }
        return runCatching {
            discoverCurrent(raster, identity, discoverySequence, carrierHints, isIdentityCurrent, isCancelled)
        }.getOrElse {
            unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.Failure,
                detail = it::class.java.simpleName,
            )
        }
    }

    private fun discoverCurrent(
        raster: ChromeVisualShieldDiscoveryRaster,
        identity: ChromeVisualShieldIdentity,
        discoverySequence: Long,
        carrierHints: List<ChromeVisualShieldCarrierHint>,
        isIdentityCurrent: () -> Boolean,
        isCancelled: () -> Boolean,
    ): ChromeVisualShieldRegionDiscoveryResult {
        val background = estimateBackground(raster)
        if (background.ambiguous) {
            return unknown(
                raster,
                carrierHints,
                if (background.perimeterForeignFraction < FullBleedMinimumPerimeterForeignFraction) {
                    ChromeVisualShieldDiscoveryUnknownReason.CutComponent
                } else {
                    ChromeVisualShieldDiscoveryUnknownReason.BackgroundAmbiguous
                },
                detail =
                    "perimeterSpread=${background.perimeterSpread}," +
                        "foreignFraction=${background.perimeterForeignFraction}," +
                        "edgeDensity=${background.edgeDensity},fullBleedAuthority=false",
            )
        }
        val borderBackground = connectedBorderBackground(raster, background)
        if (isCancelled()) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.Cancelled)
        }
        if (!isIdentityCurrent()) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.StaleIdentity)
        }
        val components = components(raster, borderBackground)
        val significant = mutableListOf<Component>()
        var certifiedNonContent = 0
        var residual = 0
        components.forEach { component ->
            when {
                component.isSignificant(raster) -> significant += component
                component.isCertifiableNonContent(raster) -> certifiedNonContent += component.pixelCount
                else -> residual += component.pixelCount
            }
        }
        if (significant.size > maximumRegions) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.RegionOverflow,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        if (significant.isEmpty()) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.InsufficientEvidence,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        if (significant.any { it.touchesBorder(raster) }) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.CutComponent,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        if (significant.any { it.pixelCount.toDouble() / it.area < MinimumClosedIslandFill }) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.OverlappingRegions,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        if (residual > max(MinimumResidualPixels, (raster.size * MaximumResidualFraction).toInt())) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.SignificantResidual,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        val ordered = significant.sortedWith(compareBy<Component>({ it.top }, { it.left }, { it.bottom }, { it.right }))
        if (ordered.indices.any { index -> ordered.drop(index + 1).any { ordered[index].overlaps(it) } }) {
            return unknown(
                raster,
                carrierHints,
                ChromeVisualShieldDiscoveryUnknownReason.OverlappingRegions,
                foregroundPixels = components.sumOf(Component::pixelCount),
                residualPixels = residual,
                componentCount = components.size,
            )
        }
        val regions =
            ordered.mapIndexed { index, component ->
                component.toRegion(index, raster)
            }
        if (regions.any { it.bounds.width <= 0 || it.bounds.height <= 0 }) {
            return unknown(raster, carrierHints, ChromeVisualShieldDiscoveryUnknownReason.InvalidGeometry)
        }
        val assignedPixels = significant.sumOf(Component::pixelCount)
        val certifiedBackground = borderBackground.count(Boolean::not).let { raster.size - it }
        val evidence =
            ChromeVisualShieldCoverageEvidence(
                totalPixels = raster.size,
                certifiedBackgroundPixels = certifiedBackground,
                certifiedNonContentPixels = certifiedNonContent,
                assignedPixels = assignedPixels,
                residualPixels = residual,
                carrierHintCount = carrierHints.count { it.visible },
                basis = "border_background+closed_components",
            )
        return ChromeVisualShieldRegionDiscoveryResult.Complete(
            regions = regions,
            discoverySequence = discoverySequence,
            regionSetDigest = digest(identity, discoverySequence, regions),
            coverageEvidence = evidence,
        )
    }

    private fun estimateBackground(raster: ChromeVisualShieldDiscoveryRaster): BackgroundEstimate {
        val perimeter = ArrayList<Int>(raster.width * 2 + raster.height * 2)
        for (x in 0 until raster.width) perimeter += raster.argb[x]
        for (y in 1 until raster.height) perimeter += raster.argb[y * raster.width + raster.width - 1]
        if (raster.height > 1) {
            for (x in raster.width - 2 downTo 0) perimeter += raster.argb[(raster.height - 1) * raster.width + x]
        }
        if (raster.width > 1) {
            for (y in raster.height - 2 downTo 1) perimeter += raster.argb[y * raster.width]
        }
        val red = perimeter.map(::red).sorted()[perimeter.size / 2]
        val green = perimeter.map(::green).sorted()[perimeter.size / 2]
        val blue = perimeter.map(::blue).sorted()[perimeter.size / 2]
        val distances = perimeter.map { colorDistance(it, red, green, blue) }.sorted()
        val spread = distances[(distances.lastIndex * PerimeterPercentile) / 100]
        return BackgroundEstimate(
            red = red,
            green = green,
            blue = blue,
            perimeterSpread = spread,
            perimeterForeignFraction =
                distances.count { it > BackgroundDistanceLimit }.toDouble() / distances.size,
            ambiguous = spread > BackgroundDistanceLimit,
            edgeDensity = perimeterEdgeDensity(perimeter),
        )
    }

    private fun connectedBorderBackground(
        raster: ChromeVisualShieldDiscoveryRaster,
        estimate: BackgroundEstimate,
    ): BooleanArray {
        val foreground = BooleanArray(raster.size)
        val queue = IntArray(raster.size)
        var head = 0
        var tail = 0

        fun enqueue(index: Int) {
            if (foreground[index] || !estimate.matches(raster.argb[index])) return
            foreground[index] = true
            queue[tail++] = index
        }

        for (x in 0 until raster.width) {
            enqueue(x)
            enqueue((raster.height - 1) * raster.width + x)
        }
        for (y in 0 until raster.height) {
            enqueue(y * raster.width)
            enqueue(y * raster.width + raster.width - 1)
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % raster.width
            val y = index / raster.width
            if (x > 0) enqueue(index - 1)
            if (x + 1 < raster.width) enqueue(index + 1)
            if (y > 0) enqueue(index - raster.width)
            if (y + 1 < raster.height) enqueue(index + raster.width)
        }
        return foreground
    }

    private fun components(
        raster: ChromeVisualShieldDiscoveryRaster,
        borderBackground: BooleanArray,
    ): List<Component> {
        val visited = borderBackground.copyOf()
        val queue = IntArray(raster.size)
        val output = mutableListOf<Component>()
        for (seed in 0 until raster.size) {
            if (visited[seed]) continue
            var head = 0
            var tail = 0
            var left = seed % raster.width
            var right = left
            var top = seed / raster.width
            var bottom = top
            var pixels = 0
            visited[seed] = true
            queue[tail++] = seed
            while (head < tail) {
                val index = queue[head++]
                val x = index % raster.width
                val y = index / raster.width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                pixels += 1

                fun visit(candidate: Int) {
                    if (visited[candidate]) return
                    visited[candidate] = true
                    queue[tail++] = candidate
                }
                if (x > 0) visit(index - 1)
                if (x + 1 < raster.width) visit(index + 1)
                if (y > 0) visit(index - raster.width)
                if (y + 1 < raster.height) visit(index + raster.width)
            }
            output += Component(left, top, right, bottom, pixels)
        }
        return output
    }

    private fun Component.toRegion(
        index: Int,
        raster: ChromeVisualShieldDiscoveryRaster,
    ): ChromeVisualShieldDiscoveredRegion {
        val signature = visualSignature(this, raster)
        val id = "discovery-${index + 1}-${signature.take(12)}"
        return ChromeVisualShieldDiscoveredRegion(
            id = id,
            bounds = ChromeVisualRegion(id, left, top, right + 1, bottom + 1),
            visualSignature = signature,
            assignedPixels = pixelCount,
        )
    }

    private fun visualSignature(
        component: Component,
        raster: ChromeVisualShieldDiscoveryRaster,
    ): String {
        val summary = StringBuilder()
        summary.append(component.left).append(',').append(component.top).append(',')
            .append(component.right).append(',').append(component.bottom).append('|')
        for (gridY in 0 until SignatureGrid) {
            for (gridX in 0 until SignatureGrid) {
                val x = component.left + (component.width - 1) * (gridX * 2 + 1) / (SignatureGrid * 2)
                val y = component.top + (component.height - 1) * (gridY * 2 + 1) / (SignatureGrid * 2)
                val pixel = raster.argb[y * raster.width + x]
                summary.append(red(pixel) / SignatureBucket).append(':')
                    .append(green(pixel) / SignatureBucket).append(':')
                    .append(blue(pixel) / SignatureBucket).append(';')
            }
        }
        return sha256(summary.toString())
    }

    private fun digest(
        identity: ChromeVisualShieldIdentity,
        discoverySequence: Long,
        regions: List<ChromeVisualShieldDiscoveredRegion>,
    ): String =
        sha256(
            buildString {
                append(identity.protectionSessionId).append('|')
                append(identity.windowId).append('|')
                append(identity.contentEpoch).append('|')
                append(identity.viewportEpoch).append('|')
                append(identity.captureSequence).append('|')
                append(discoverySequence)
                regions.forEach { region ->
                    append('|').append(region.id).append(':').append(region.bounds.left).append(',')
                    append(region.bounds.top).append(',').append(region.bounds.right).append(',')
                    append(region.bounds.bottom).append(':').append(region.visualSignature)
                }
            },
        )

    private fun perimeterEdgeDensity(perimeter: List<Int>): Double {
        if (perimeter.size < 2) return 0.0
        val edges =
            perimeter.indices.count { index ->
                colorDistance(perimeter[index], perimeter[(index + 1) % perimeter.size]) > EdgeDistanceLimit
            }
        return edges.toDouble() / perimeter.size
    }

    private fun unknown(
        raster: ChromeVisualShieldDiscoveryRaster,
        hints: List<ChromeVisualShieldCarrierHint>,
        reason: ChromeVisualShieldDiscoveryUnknownReason,
        foregroundPixels: Int = 0,
        residualPixels: Int = 0,
        componentCount: Int = 0,
        detail: String = reason.name,
    ) = ChromeVisualShieldRegionDiscoveryResult.Unknown(
        reason,
        ChromeVisualShieldResidualEvidence(
            totalPixels = raster.size.coerceAtLeast(0),
            foregroundPixels = foregroundPixels,
            residualPixels = residualPixels,
            componentCount = componentCount,
            carrierHintCount = hints.count { it.visible },
            detail = detail,
        ),
    )

    private data class BackgroundEstimate(
        val red: Int,
        val green: Int,
        val blue: Int,
        val perimeterSpread: Int,
        val perimeterForeignFraction: Double,
        val ambiguous: Boolean,
        val edgeDensity: Double,
    ) {
        fun matches(pixel: Int): Boolean = colorDistance(pixel, red, green, blue) <= BackgroundDistanceLimit
    }

    private data class Component(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val pixelCount: Int,
    ) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val area: Int get() = width * height

        fun isSignificant(raster: ChromeVisualShieldDiscoveryRaster): Boolean =
            width >= MinimumRegionDimension &&
                height >= MinimumRegionDimension &&
                area >= max(MinimumRegionArea, (raster.size * MinimumRegionAreaFraction).toInt())

        fun isCertifiableNonContent(raster: ChromeVisualShieldDiscoveryRaster): Boolean =
            pixelCount <= max(MinimumResidualPixels, (raster.size * CertifiedDetailFraction).toInt()) &&
                (width < MinimumRegionDimension || height < MinimumRegionDimension)

        fun touchesBorder(raster: ChromeVisualShieldDiscoveryRaster): Boolean =
            left == 0 || top == 0 || right == raster.width - 1 || bottom == raster.height - 1

        fun overlaps(other: Component): Boolean =
            left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
    }

    private companion object {
        const val DefaultMaximumRegions = 8
        const val MinimumRegionDimension = 8
        const val MinimumRegionArea = 64
        const val MinimumRegionAreaFraction = 0.0025
        const val CertifiedDetailFraction = 0.0005
        const val MaximumResidualFraction = 0.005
        const val MinimumClosedIslandFill = 0.92
        const val MinimumResidualPixels = 12
        const val PerimeterPercentile = 90
        const val BackgroundDistanceLimit = 70
        const val EdgeDistanceLimit = 120
        const val FullBleedMinimumPerimeterForeignFraction = 0.45
        const val SignatureGrid = 4
        const val SignatureBucket = 32

        fun red(pixel: Int): Int = pixel ushr 16 and 0xff

        fun green(pixel: Int): Int = pixel ushr 8 and 0xff

        fun blue(pixel: Int): Int = pixel and 0xff

        fun colorDistance(
            pixel: Int,
            red: Int,
            green: Int,
            blue: Int,
        ): Int =
            maxOf(
                kotlin.math.abs(red(pixel) - red),
                kotlin.math.abs(green(pixel) - green),
                kotlin.math.abs(blue(pixel) - blue),
            )

        fun colorDistance(
            first: Int,
            second: Int,
        ): Int = colorDistance(first, red(second), green(second), blue(second))

        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
