package com.contentfilter.user.chromedataplane

/** A bounded, decoded-order timeline. Pixel decoding remains Android's responsibility. */
internal data class ChromeGifFrame(
    val sampleTimeMillis: Int,
    val durationMillis: Int,
)

internal data class ChromeGifTimeline(
    val width: Int,
    val height: Int,
    val durationMillis: Int,
    val frames: List<ChromeGifFrame>,
)

internal sealed interface ChromeGifTimelineResult {
    data object NotGif : ChromeGifTimelineResult

    data object StaticGif : ChromeGifTimelineResult

    data class Animated(
        val timeline: ChromeGifTimeline,
    ) : ChromeGifTimelineResult

    data class Rejected(
        val reason: String,
    ) : ChromeGifTimelineResult
}

/**
 * Parses the GIF structure without decoding pixels. Every image descriptor is retained as an
 * analysis sample, so a short unsafe frame cannot be hidden by a time-only sampler.
 */
internal object ChromeGifTimelineParser {
    fun parse(bytes: ByteArray): ChromeGifTimelineResult {
        if (!hasSignature(bytes)) return ChromeGifTimelineResult.NotGif
        if (bytes.size > MaximumBytes) return rejected(TooLargeReason)

        val cursor = Cursor(bytes, HeaderBytes)
        val width = cursor.readLe16() ?: return rejected(MalformedReason)
        val height = cursor.readLe16() ?: return rejected(MalformedReason)
        val packed = cursor.readU8() ?: return rejected(MalformedReason)
        if (cursor.skip(2).not()) return rejected(MalformedReason)
        if (packed and ReservedLogicalScreenBits != 0) return rejected(MalformedReason)
        if (!safeDimensions(width, height)) return rejected(DimensionsReason)
        if (hasColorTable(packed) && !cursor.skip(colorTableBytes(packed))) return rejected(MalformedReason)

        val frames = mutableListOf<ChromeGifFrame>()
        var pendingDelayMillis = DefaultFrameDurationMillis
        var elapsedMillis = 0
        var trailerSeen = false
        while (cursor.hasRemaining()) {
            when (cursor.readU8()) {
                ExtensionIntroducer -> {
                    val label = cursor.readU8() ?: return rejected(MalformedReason)
                    if (label == GraphicControlLabel) {
                        val blockSize = cursor.readU8() ?: return rejected(MalformedReason)
                        if (blockSize != GraphicControlBlockSize) return rejected(MalformedReason)
                        val controlPacked = cursor.readU8() ?: return rejected(MalformedReason)
                        if (controlPacked and ReservedGraphicControlBits != 0) {
                            return rejected(MalformedReason)
                        }
                        val delayHundredths = cursor.readLe16() ?: return rejected(MalformedReason)
                        if (cursor.readU8() == null) return rejected(MalformedReason)
                        if (cursor.readU8() != 0) return rejected(MalformedReason)
                        pendingDelayMillis = normalizedDelayMillis(delayHundredths)
                    } else if (!cursor.skipSubBlocks()) {
                        return rejected(MalformedReason)
                    }
                }

                ImageDescriptor -> {
                    val left = cursor.readLe16() ?: return rejected(MalformedReason)
                    val top = cursor.readLe16() ?: return rejected(MalformedReason)
                    val frameWidth = cursor.readLe16() ?: return rejected(MalformedReason)
                    val frameHeight = cursor.readLe16() ?: return rejected(MalformedReason)
                    val imagePacked = cursor.readU8() ?: return rejected(MalformedReason)
                    if (imagePacked and ReservedImageDescriptorBits != 0) {
                        return rejected(MalformedReason)
                    }
                    if (
                        frameWidth <= 0 ||
                        frameHeight <= 0 ||
                        left.toLong() + frameWidth > width ||
                        top.toLong() + frameHeight > height
                    ) {
                        return rejected(FrameBoundsReason)
                    }
                    if (hasColorTable(imagePacked) && !cursor.skip(colorTableBytes(imagePacked))) {
                        return rejected(MalformedReason)
                    }
                    val minimumCodeSize = cursor.readU8() ?: return rejected(MalformedReason)
                    if (minimumCodeSize !in MinimumLzwCodeSize..MaximumLzwCodeSize) {
                        return rejected(MalformedReason)
                    }
                    if (!cursor.skipSubBlocks()) return rejected(MalformedReason)
                    if (frames.size >= MaximumFrameCount) return rejected(FrameLimitReason)
                    val nextElapsed = elapsedMillis.toLong() + pendingDelayMillis
                    if (nextElapsed > MaximumDurationMillis) return rejected(DurationLimitReason)
                    frames +=
                        ChromeGifFrame(
                            sampleTimeMillis = elapsedMillis + pendingDelayMillis / 2,
                            durationMillis = pendingDelayMillis,
                        )
                    elapsedMillis = nextElapsed.toInt()
                    pendingDelayMillis = DefaultFrameDurationMillis
                }

                Trailer -> {
                    trailerSeen = true
                    break
                }

                else -> return rejected(MalformedReason)
            }
        }
        if (trailerSeen && cursor.hasRemaining()) return rejected(MalformedReason)
        if (!trailerSeen || frames.isEmpty()) return rejected(MalformedReason)
        if (frames.size == 1) return ChromeGifTimelineResult.StaticGif
        return ChromeGifTimelineResult.Animated(
            ChromeGifTimeline(width, height, elapsedMillis, frames.toList()),
        )
    }

    private fun hasSignature(bytes: ByteArray): Boolean =
        bytes.size >= MinimumBytes &&
            (bytes.matchesAsciiLocal(0, "GIF87a") || bytes.matchesAsciiLocal(0, "GIF89a"))

    private fun safeDimensions(
        width: Int,
        height: Int,
    ): Boolean =
        width in 1..MaximumDimension &&
            height in 1..MaximumDimension &&
            width.toLong() * height <= MaximumPixels

    private fun hasColorTable(packed: Int): Boolean = packed and ColorTableFlag != 0

    private fun colorTableBytes(packed: Int): Int = ColorTableChannels * (1 shl ((packed and ColorTableSizeMask) + 1))

    private fun normalizedDelayMillis(delayHundredths: Int): Int =
        if (delayHundredths < MinimumReliableDelayHundredths) {
            DefaultFrameDurationMillis
        } else {
            (delayHundredths * HundredthMillis).coerceAtMost(MaximumFrameDurationMillis)
        }

    private fun rejected(reason: String) = ChromeGifTimelineResult.Rejected(reason)

    private class Cursor(
        private val bytes: ByteArray,
        private var index: Int,
    ) {
        fun hasRemaining(): Boolean = index < bytes.size

        fun readU8(): Int? = if (index < bytes.size) bytes[index++].toInt() and 0xff else null

        fun readLe16(): Int? {
            val low = readU8() ?: return null
            val high = readU8() ?: return null
            return low or (high shl 8)
        }

        fun skip(count: Int): Boolean {
            if (count < 0 || count > bytes.size - index) return false
            index += count
            return true
        }

        fun skipSubBlocks(): Boolean {
            while (true) {
                val size = readU8() ?: return false
                if (size == 0) return true
                if (!skip(size)) return false
            }
        }
    }

    private fun ByteArray.matchesAsciiLocal(
        offset: Int,
        value: String,
    ): Boolean =
        offset >= 0 &&
            offset + value.length <= size &&
            value.indices.all { index -> this[offset + index].toInt() == value[index].code }

    const val MaximumFrameCount = 120
    const val MaximumDurationMillis = 60_000
    const val DefaultFrameDurationMillis = 100
    const val MalformedReason = "gif_malformed"
    const val TooLargeReason = "gif_too_large"
    const val DimensionsReason = "gif_dimensions"
    const val FrameBoundsReason = "gif_frame_bounds"
    const val FrameLimitReason = "gif_frame_limit"
    const val DurationLimitReason = "gif_duration_limit"

    private const val HeaderBytes = 6
    private const val MinimumBytes = 14
    private const val MaximumBytes = 16 * 1024 * 1024
    private const val MaximumDimension = 4_096
    private const val MaximumPixels = 16_777_216L
    private const val ColorTableFlag = 0x80
    private const val ColorTableSizeMask = 0x07
    private const val ColorTableChannels = 3
    private const val ReservedLogicalScreenBits = 0x08
    private const val ReservedGraphicControlBits = 0xe0
    private const val ReservedImageDescriptorBits = 0x18
    private const val ExtensionIntroducer = 0x21
    private const val GraphicControlLabel = 0xf9
    private const val GraphicControlBlockSize = 4
    private const val ImageDescriptor = 0x2c
    private const val Trailer = 0x3b
    private const val MinimumLzwCodeSize = 2
    private const val MaximumLzwCodeSize = 8
    private const val MinimumReliableDelayHundredths = 2
    private const val HundredthMillis = 10
    private const val MaximumFrameDurationMillis = 10_000
}
