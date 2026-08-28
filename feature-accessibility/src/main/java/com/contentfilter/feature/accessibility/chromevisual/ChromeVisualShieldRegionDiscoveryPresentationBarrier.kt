package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.floor

/** DEV-only marker declared by the signed R2A fixture. It is never an R2B release input. */
data class ChromeVisualShieldRegionDiscoveryPresentationProof(
    val pattern: String,
    val markerCanvas: ChromeVisualShieldLabRect,
    val cellWidth: Int,
) {
    fun isStructurallyValid(
        canvasWidth: Int,
        canvasHeight: Int,
    ): Boolean =
        pattern.length == ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.PatternBits &&
            pattern.all { it == '0' || it == '1' } &&
            pattern.startsWith(ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.PatternPrefix) &&
            cellWidth == ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.CellWidthPixels &&
            markerCanvas.isFinitePositive() &&
            markerCanvas.left >= 0.0 &&
            markerCanvas.top == 0.0 &&
            markerCanvas.right <= canvasWidth &&
            markerCanvas.bottom <= canvasHeight &&
            markerCanvas.width == pattern.length * cellWidth.toDouble() &&
            markerCanvas.height == ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.MarkerHeightPixels
}

/** Cryptographically binds the visible marker pattern to the complete native render generation. */
object ChromeVisualShieldRegionDiscoveryPresentationMarkerContract {
    const val PatternPrefix = "10"
    const val PatternBits = 130
    const val CellWidthPixels = 3
    const val MarkerHeightPixels = 3.0
    const val ZeroCss = "#ff00a8"
    const val OneCss = "#35ff5d"
    internal const val ZeroRgb = 0xff00a8
    internal const val OneRgb = 0x35ff5d

    fun expected(
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        canvasWidth: Int,
        canvasHeight: Int,
    ): ChromeVisualShieldRegionDiscoveryPresentationProof? {
        if (!binding.isStructurallyValid() || canvasWidth <= 0 || canvasHeight < MarkerHeightPixels) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(binding.canonicalMarkerBytes())
        val payload =
            buildString(PatternBits - PatternPrefix.length) {
                repeat(PatternBits - PatternPrefix.length) { bitIndex ->
                    val byte = digest[bitIndex / 8].toInt() and 0xff
                    append(if ((byte and (1 shl (7 - bitIndex % 8))) == 0) '0' else '1')
                }
            }
        val pattern = PatternPrefix + payload
        val width = pattern.length * CellWidthPixels
        if (width > canvasWidth) return null
        return ChromeVisualShieldRegionDiscoveryPresentationProof(
            pattern = pattern,
            markerCanvas =
                ChromeVisualShieldLabRect(
                    left = floor((canvasWidth - width) / 2.0),
                    top = 0.0,
                    width = width.toDouble(),
                    height = MarkerHeightPixels,
                ),
            cellWidth = CellWidthPixels,
        )
    }

    private fun ChromeVisualShieldRegionDiscoveryRenderBinding.canonicalMarkerBytes(): ByteArray =
        listOf(
            protectionSessionId,
            windowId,
            contentEpoch,
            viewportEpoch,
            regionSequence,
            renderIdentityToken,
            renderGeometryKeyDigest,
        ).joinToString("|").toByteArray(Charsets.UTF_8)
}

internal enum class ChromeVisualShieldRegionDiscoveryPresentationRejectReason {
    BindingMismatch,
    ProofMissing,
    ProofContractMismatch,
    GeometryMismatch,
    MarkerAbsent,
    MarkerStaleOrCorrupt,
}

internal sealed interface ChromeVisualShieldRegionDiscoveryPresentationResult {
    data class Proven(
        val markerBounds: ChromeVisualRegion,
        val matchedSamples: Int,
    ) : ChromeVisualShieldRegionDiscoveryPresentationResult

    data class Rejected(
        val reason: ChromeVisualShieldRegionDiscoveryPresentationRejectReason,
        val matchedSamples: Int,
        val paletteSamples: Int,
    ) : ChromeVisualShieldRegionDiscoveryPresentationResult
}

internal data class ChromeVisualShieldRegionDiscoveryPresentationMetricsSnapshot(
    val observed: Long,
    val rejected: Long,
    val absent: Long,
    val staleOrCorrupt: Long,
)

/**
 * Reads the actual Android screenshot before crop/planner. It never mutates pixels and its marker
 * rectangle must remain wholly outside the R2A search envelope.
 */
internal class ChromeVisualShieldRegionDiscoveryPresentationBarrier {
    private val observed = AtomicLong(0)
    private val rejected = AtomicLong(0)
    private val absent = AtomicLong(0)
    private val staleOrCorrupt = AtomicLong(0)

    fun verify(
        bitmap: Bitmap,
        identity: ChromeVisualShieldIdentity,
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        oracle: ChromeVisualShieldRegionDiscoveryOracle?,
    ): ChromeVisualShieldRegionDiscoveryPresentationResult =
        verify(
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            pixelAt = bitmap::getPixel,
            identity = identity,
            binding = binding,
            oracle = oracle,
        )

    internal fun verify(
        frameWidth: Int,
        frameHeight: Int,
        pixelAt: (Int, Int) -> Int,
        identity: ChromeVisualShieldIdentity,
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        oracle: ChromeVisualShieldRegionDiscoveryOracle?,
    ): ChromeVisualShieldRegionDiscoveryPresentationResult {
        if (!binding.matches(
                identity,
            )
        ) {
            return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.BindingMismatch)
        }
        val value = oracle ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.ProofMissing)
        val proof =
            value.presentationProof
                ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.ProofMissing)
        val expected =
            ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(
                binding,
                value.canvasWidth,
                value.canvasHeight,
            )
        if (proof != expected || !proof.isStructurallyValid(value.canvasWidth, value.canvasHeight)) {
            return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.ProofContractMismatch)
        }
        val mappedCarrier =
            ChromeVisualShieldBrowserViewportMapper.map(
                source = value.carrierCss,
                target = identity.viewport,
                visualViewport = value.visualViewportCss,
                devicePixelRatio = value.devicePixelRatio,
                visualViewportScale = value.visualViewportScale,
                navigationInsets = value.navigationInsets,
                id = "presentation-proof-carrier",
            ) ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.GeometryMismatch)
        val markerGlobal = proof.markerCanvas.mapInside(mappedCarrier, value.canvasWidth, value.canvasHeight)
        val markerFrame =
            ChromeVisualGeometryMapper.toFrame(markerGlobal, identity.viewport, frameWidth, frameHeight)
                ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.GeometryMismatch)
        val searchFrame =
            ChromeVisualGeometryMapper.toFrame(identity.region, identity.viewport, frameWidth, frameHeight)
                ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.GeometryMismatch)
        if (markerFrame.intersects(searchFrame)) {
            return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.GeometryMismatch)
        }

        var matched = 0
        var palette = 0
        proof.pattern.forEachIndexed { index, bit ->
            val cellCanvas =
                ChromeVisualShieldLabRect(
                    left = proof.markerCanvas.left + index * proof.cellWidth,
                    top = proof.markerCanvas.top,
                    width = proof.cellWidth.toDouble(),
                    height = proof.markerCanvas.height,
                )
            val cellGlobal = cellCanvas.mapInside(mappedCarrier, value.canvasWidth, value.canvasHeight)
            val cellFrame =
                ChromeVisualGeometryMapper.toFrame(cellGlobal, identity.viewport, frameWidth, frameHeight)
                    ?: return reject(ChromeVisualShieldRegionDiscoveryPresentationRejectReason.GeometryMismatch)
            val x = ((cellFrame.left + cellFrame.right - 1) / 2).coerceIn(0, frameWidth - 1)
            val y = ((cellFrame.top + cellFrame.bottom - 1) / 2).coerceIn(0, frameHeight - 1)
            val pixel = pixelAt(x, y)
            val expectedRgb =
                if (bit == '0') {
                    ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.ZeroRgb
                } else {
                    ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.OneRgb
                }
            if (pixel.nearRgb(expectedRgb)) matched += 1
            if (
                pixel.nearRgb(ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.ZeroRgb) ||
                pixel.nearRgb(ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.OneRgb)
            ) {
                palette += 1
            }
        }
        return when {
            matched == proof.pattern.length -> {
                observed.incrementAndGet()
                ChromeVisualShieldRegionDiscoveryPresentationResult.Proven(markerFrame, matched)
            }
            palette == 0 ->
                reject(
                    ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerAbsent,
                    matched,
                    palette,
                )
            else ->
                reject(
                    ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerStaleOrCorrupt,
                    matched,
                    palette,
                )
        }
    }

    fun snapshot() =
        ChromeVisualShieldRegionDiscoveryPresentationMetricsSnapshot(
            observed.get(),
            rejected.get(),
            absent.get(),
            staleOrCorrupt.get(),
        )

    fun reset() {
        observed.set(0)
        rejected.set(0)
        absent.set(0)
        staleOrCorrupt.set(0)
    }

    private fun reject(
        reason: ChromeVisualShieldRegionDiscoveryPresentationRejectReason,
        matched: Int = 0,
        palette: Int = 0,
    ): ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected {
        rejected.incrementAndGet()
        if (reason == ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerAbsent) absent.incrementAndGet()
        if (reason == ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerStaleOrCorrupt) {
            staleOrCorrupt.incrementAndGet()
        }
        return ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected(reason, matched, palette)
    }

    private fun ChromeVisualShieldLabRect.mapInside(
        carrier: ChromeVisualRegion,
        canvasWidth: Int,
        canvasHeight: Int,
    ): ChromeVisualRegion =
        ChromeVisualRegion(
            id = "presentation-proof",
            left = floor(carrier.left + carrier.width * left / canvasWidth).toInt(),
            top = floor(carrier.top + carrier.height * top / canvasHeight).toInt(),
            right = ceil(carrier.left + carrier.width * right / canvasWidth).toInt(),
            bottom = ceil(carrier.top + carrier.height * bottom / canvasHeight).toInt(),
        )

    private fun ChromeVisualRegion.intersects(other: ChromeVisualRegion): Boolean =
        minOf(right, other.right) > maxOf(left, other.left) &&
            minOf(bottom, other.bottom) > maxOf(top, other.top)

    private fun Int.nearRgb(expected: Int): Boolean {
        val red = this shr 16 and 0xff
        val green = this shr 8 and 0xff
        val blue = this and 0xff
        return kotlin.math.abs(red - (expected shr 16 and 0xff)) <= ColorTolerance &&
            kotlin.math.abs(green - (expected shr 8 and 0xff)) <= ColorTolerance &&
            kotlin.math.abs(blue - (expected and 0xff)) <= ColorTolerance
    }

    private companion object {
        const val ColorTolerance = 24
    }
}
