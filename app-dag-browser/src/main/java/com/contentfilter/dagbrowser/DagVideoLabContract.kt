package com.contentfilter.dagbrowser

import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class DagVideoLabKey(
    val tabId: Long,
    val documentToken: String,
    val videoId: String,
    val revision: Int,
)

/**
 * Identifies one immutable captured video frame.
 *
 * The page-provided video revision alone is not enough to authorize a replay: a viewport movement
 * or a late callback can otherwise bind pixels from a different displayed frame. Both counters are
 * monotonically issued by the content-side authority for the active video revision.
 */
internal data class DagVideoLabFrameKey(
    val videoKey: DagVideoLabKey,
    val viewportEpoch: Int,
    val frameSequence: Int,
) {
    fun isValid(): Boolean {
        return viewportEpoch in 1..MaximumCounter && frameSequence in 1..MaximumCounter
    }

    private companion object {
        const val MaximumCounter = 1_000_000
    }
}

/**
 * A close request is acknowledged only after the extension has removed its temporary raw-video
 * permission. A nonce makes a late acknowledgement from an older close attempt harmless.
 */
internal data class DagVideoLabCloseRequest(
    val key: DagVideoLabKey,
    val nonce: String,
) {
    fun isValid(): Boolean = CloseNoncePattern.matches(nonce)

    private companion object {
        val CloseNoncePattern = Regex("^[a-f0-9]{32}$")
    }
}

/**
 * PixelCopy scales exactly to the requested bitmap dimensions. This plan preserves the source
 * aspect ratio while bounding allocation before the same bitmap is letterboxed for R3.1.
 */
internal data class DagVideoLabCapturePlan(
    val targetWidth: Int,
    val targetHeight: Int,
) {
    companion object {
        const val DefaultMaxLongEdge = 512
        const val MaximumLongEdge = DefaultMaxLongEdge

        fun fromSurfaceRect(
            surfaceRect: Rect,
            maxLongEdge: Int = DefaultMaxLongEdge,
        ): DagVideoLabCapturePlan? =
            fromDimensions(
                sourceWidth = surfaceRect.width(),
                sourceHeight = surfaceRect.height(),
                maxLongEdge = maxLongEdge,
            )

        fun fromDimensions(
            sourceWidth: Int,
            sourceHeight: Int,
            maxLongEdge: Int = DefaultMaxLongEdge,
        ): DagVideoLabCapturePlan? {
            if (
                sourceWidth <= 0 ||
                sourceHeight <= 0 ||
                maxLongEdge !in 1..MaximumLongEdge
            ) {
                return null
            }
            val sourceLongEdge = max(sourceWidth, sourceHeight)
            val scale = min(1.0, maxLongEdge.toDouble() / sourceLongEdge.toDouble())
            return DagVideoLabCapturePlan(
                targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

internal data class DagVideoLabClientRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    fun isValid(): Boolean {
        val values = listOf(left, top, width, height, viewportWidth, viewportHeight)
        if (values.any { !it.isFinite() }) return false
        if (width < MinimumEdge || height < MinimumEdge) return false
        if (viewportWidth !in MinimumEdge..MaximumEdge) return false
        if (viewportHeight !in MinimumEdge..MaximumEdge) return false
        if (width > MaximumEdge || height > MaximumEdge) return false
        val right = left + width
        val bottom = top + height
        return right > 0f && bottom > 0f && left < viewportWidth && top < viewportHeight
    }

    private companion object {
        const val MinimumEdge = 2f
        const val MaximumEdge = 16_384f
    }
}

internal enum class DagVideoLabState {
    Covering,
    Covered,
    Capturing,
    Failed,
    Closing,
    Blocked,
}

/**
 * Owns one diagnostic video revision and one capture at a time.
 *
 * The state contains no Android objects, bitmaps or page data. Late callbacks can only advance the
 * exact active key; navigation, source replacement and authority changes therefore fail closed.
 */
internal class DagVideoLabStateMachine {
    private data class Active(
        val key: DagVideoLabKey,
        var state: DagVideoLabState,
        var frameKey: DagVideoLabFrameKey? = null,
        var lastFrameSequence: Int = 0,
        var closeNonce: String? = null,
    )

    private var active: Active? = null

    val currentKey: DagVideoLabKey?
        get() = active?.key

    val currentState: DagVideoLabState?
        get() = active?.state

    val currentFrameKey: DagVideoLabFrameKey?
        get() = active?.frameKey

    val currentCloseNonce: String?
        get() = active?.closeNonce

    fun requestCover(
        key: DagVideoLabKey,
        rect: DagVideoLabClientRect,
    ): Boolean {
        if (!validKey(key) || !rect.isValid()) return false
        // A new authority must never overwrite a covered, failed or closing
        // revision. The caller must keep the Android cover and complete the
        // exact revocation handshake first, then create a fresh state.
        if (active !== null) return false
        active = Active(key, DagVideoLabState.Covering)
        return true
    }

    fun markCovered(key: DagVideoLabKey): Boolean =
        transition(
            key = key,
            expected = DagVideoLabState.Covering,
            next = DagVideoLabState.Covered,
        )

    fun requestCapture(
        frameKey: DagVideoLabFrameKey,
        rect: DagVideoLabClientRect,
    ): Boolean {
        val current = active ?: return false
        if (
            !rect.isValid() ||
            !frameKey.isValid() ||
            current.key != frameKey.videoKey ||
            current.state != DagVideoLabState.Covered ||
            frameKey.frameSequence <= current.lastFrameSequence
        ) {
            return false
        }
        current.frameKey = frameKey
        current.lastFrameSequence = frameKey.frameSequence
        current.state = DagVideoLabState.Capturing
        return true
    }

    fun completeCapture(
        frameKey: DagVideoLabFrameKey,
        captured: Boolean,
    ): Boolean {
        val current = active ?: return false
        if (
            current.key != frameKey.videoKey ||
            current.frameKey != frameKey ||
            current.state != DagVideoLabState.Capturing
        ) {
            return false
        }
        current.state = if (captured) DagVideoLabState.Covered else DagVideoLabState.Failed
        return true
    }

    fun fail(key: DagVideoLabKey): Boolean {
        val current = active ?: return false
        if (current.key != key) return false
        current.state = DagVideoLabState.Failed
        current.frameKey = null
        return true
    }

    /**
     * Begins a fail-closed teardown. The Android cover remains in place until the exact nonce is
     * acknowledged; timeout or a disconnect must call [blockClosing] instead of exposing Gecko.
     */
    fun beginClosing(
        key: DagVideoLabKey,
        nonce: String,
    ): Boolean {
        val current = active ?: return false
        if (
            current.key != key ||
            current.state == DagVideoLabState.Closing ||
            current.state == DagVideoLabState.Blocked ||
            !DagVideoLabCloseRequest(key, nonce).isValid()
        ) {
            return false
        }
        current.state = DagVideoLabState.Closing
        current.frameKey = null
        current.closeNonce = nonce
        return true
    }

    fun acknowledgeClose(
        key: DagVideoLabKey,
        nonce: String,
    ): Boolean {
        val current = active ?: return false
        if (
            current.key != key ||
            current.state != DagVideoLabState.Closing ||
            current.closeNonce != nonce
        ) {
            return false
        }
        active = null
        return true
    }

    fun blockClosing(
        key: DagVideoLabKey,
        nonce: String,
    ): Boolean {
        val current = active ?: return false
        if (
            current.key != key ||
            current.state != DagVideoLabState.Closing ||
            current.closeNonce != nonce
        ) {
            return false
        }
        current.state = DagVideoLabState.Blocked
        current.frameKey = null
        return true
    }

    fun isCurrent(
        key: DagVideoLabKey,
        state: DagVideoLabState? = null,
    ): Boolean {
        val current = active ?: return false
        return current.key == key && (state == null || current.state == state)
    }

    fun isCurrent(
        frameKey: DagVideoLabFrameKey,
        state: DagVideoLabState? = null,
    ): Boolean {
        val current = active ?: return false
        return (
            current.key == frameKey.videoKey &&
                current.frameKey == frameKey &&
                (state == null || current.state == state)
        )
    }

    private fun transition(
        key: DagVideoLabKey,
        expected: DagVideoLabState,
        next: DagVideoLabState,
    ): Boolean {
        val current = active ?: return false
        if (current.key != key || current.state != expected) return false
        current.state = next
        return true
    }

    private fun validKey(key: DagVideoLabKey): Boolean =
        key.tabId >= 0L &&
            DocumentTokenPattern.matches(key.documentToken) &&
            VideoIdPattern.matches(key.videoId) &&
            key.revision in 1..MaximumRevision

    private companion object {
        const val MaximumRevision = 1_000_000
        val DocumentTokenPattern = Regex("^document_[a-f0-9]{1,16}$")
        val VideoIdPattern = Regex("^video_[a-f0-9]{16}$")
    }
}

internal enum class DagVideoLabFixtureColor {
    Red,
    Green,
    Blue,
    LightNeutral,
}

internal object DagVideoLabFixtureProbe {
    fun matches(
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int,
        expectedTopLeft: DagVideoLabFixtureColor = DagVideoLabFixtureColor.Red,
        expectedTopRight: DagVideoLabFixtureColor = DagVideoLabFixtureColor.Green,
        expectedBottomLeft: DagVideoLabFixtureColor = DagVideoLabFixtureColor.Blue,
        expectedBottomRight: DagVideoLabFixtureColor = DagVideoLabFixtureColor.LightNeutral,
    ): Boolean =
        matches(topLeft, expectedTopLeft) &&
            matches(topRight, expectedTopRight) &&
            matches(bottomLeft, expectedBottomLeft) &&
            matches(bottomRight, expectedBottomRight)

    private fun matches(
        pixel: Int,
        expected: DagVideoLabFixtureColor,
    ): Boolean =
        when (expected) {
            DagVideoLabFixtureColor.Red -> isRed(pixel)
            DagVideoLabFixtureColor.Green -> isGreen(pixel)
            DagVideoLabFixtureColor.Blue -> isBlue(pixel)
            DagVideoLabFixtureColor.LightNeutral -> isLightNeutral(pixel)
        }

    private fun red(pixel: Int): Int = pixel ushr 16 and 0xff

    private fun green(pixel: Int): Int = pixel ushr 8 and 0xff

    private fun blue(pixel: Int): Int = pixel and 0xff

    private fun isRed(pixel: Int): Boolean =
        red(pixel) >= 170 && red(pixel) >= green(pixel) + 70 && red(pixel) >= blue(pixel) + 70

    private fun isGreen(pixel: Int): Boolean =
        green(pixel) >= 140 && green(pixel) >= red(pixel) + 50 && green(pixel) >= blue(pixel) + 50

    private fun isBlue(pixel: Int): Boolean =
        blue(pixel) >= 170 && blue(pixel) >= red(pixel) + 70 && blue(pixel) >= green(pixel) + 50

    private fun isLightNeutral(pixel: Int): Boolean {
        val channels = listOf(red(pixel), green(pixel), blue(pixel))
        return channels.min() >= 180 && channels.max() - channels.min() <= 50
    }
}
