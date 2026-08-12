package com.contentfilter.dagbrowser

internal data class DagVideoLabKey(
    val tabId: Long,
    val documentToken: String,
    val videoId: String,
    val revision: Int,
)

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
    )

    private var active: Active? = null

    val currentKey: DagVideoLabKey?
        get() = active?.key

    val currentState: DagVideoLabState?
        get() = active?.state

    fun requestCover(
        key: DagVideoLabKey,
        rect: DagVideoLabClientRect,
    ): Boolean {
        if (!validKey(key) || !rect.isValid()) return false
        if (active?.state == DagVideoLabState.Capturing) return false
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
        key: DagVideoLabKey,
        rect: DagVideoLabClientRect,
    ): Boolean {
        if (!rect.isValid()) return false
        return transition(
            key = key,
            expected = DagVideoLabState.Covered,
            next = DagVideoLabState.Capturing,
        )
    }

    fun completeCapture(
        key: DagVideoLabKey,
        captured: Boolean,
    ): Boolean =
        transition(
            key = key,
            expected = DagVideoLabState.Capturing,
            next = if (captured) DagVideoLabState.Covered else DagVideoLabState.Failed,
        )

    fun fail(key: DagVideoLabKey): Boolean {
        val current = active ?: return false
        if (current.key != key) return false
        current.state = DagVideoLabState.Failed
        return true
    }

    fun retire(key: DagVideoLabKey): Boolean {
        if (active?.key != key) return false
        active = null
        return true
    }

    fun retireAll(): DagVideoLabKey? = active?.key.also { active = null }

    fun isCurrent(
        key: DagVideoLabKey,
        state: DagVideoLabState? = null,
    ): Boolean {
        val current = active ?: return false
        return current.key == key && (state == null || current.state == state)
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
