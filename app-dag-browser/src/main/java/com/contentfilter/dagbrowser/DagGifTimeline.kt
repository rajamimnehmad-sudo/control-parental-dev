package com.contentfilter.dagbrowser

internal data class DagGifFrame(
    val sampleTimeMillis: Int,
    val durationMillis: Int,
)

internal data class DagGifTimeline(
    val width: Int,
    val height: Int,
    val durationMillis: Int,
    val frames: List<DagGifFrame>,
)

internal sealed interface DagGifTimelineResult {
    data object NotGif : DagGifTimelineResult

    data object StaticGif : DagGifTimelineResult

    data class Animated(
        val timeline: DagGifTimeline,
    ) : DagGifTimelineResult

    data class Rejected(
        val reason: String,
    ) : DagGifTimelineResult
}

/**
 * Reads only the bounded GIF container timeline. Pixel decoding remains Android's responsibility.
 * Every displayed image descriptor becomes one analysis sample, so a brief unsafe frame cannot be
 * skipped by a time-based sampler.
 */
internal object DagGifTimelineParser {
    fun parse(bytes: ByteArray): DagGifTimelineResult {
        if (!hasGifSignature(bytes)) return DagGifTimelineResult.NotGif
        if (bytes.size > DagMediaBytesPolicy.MaxCaptureBytes) return rejected(ResourceTooLargeReason)

        val cursor = Cursor(bytes, GifHeaderLength)
        val width = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
        val height = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
        val packed = cursor.readUnsignedByte() ?: return rejected(MalformedReason)
        if (cursor.skip(2).not()) return rejected(MalformedReason)
        if (!DagImageDecodeContract.hasSafeDimensions(width, height)) {
            return rejected(UnsafeDimensionsReason)
        }
        if (hasColorTable(packed) && !cursor.skip(colorTableByteCount(packed))) {
            return rejected(MalformedReason)
        }

        val frames = mutableListOf<DagGifFrame>()
        var pendingDelayMillis = DefaultFrameDurationMillis
        var elapsedMillis = 0
        var trailerSeen = false

        while (cursor.hasRemaining()) {
            when (cursor.readUnsignedByte()) {
                ExtensionIntroducer -> {
                    val label = cursor.readUnsignedByte() ?: return rejected(MalformedReason)
                    if (label == GraphicControlLabel) {
                        val blockSize = cursor.readUnsignedByte() ?: return rejected(MalformedReason)
                        if (blockSize != GraphicControlBlockSize) return rejected(MalformedReason)
                        if (cursor.readUnsignedByte() == null) return rejected(MalformedReason)
                        val delayHundredths =
                            cursor.readLittleEndian16() ?: return rejected(MalformedReason)
                        if (cursor.readUnsignedByte() == null || cursor.readUnsignedByte() != 0) {
                            return rejected(MalformedReason)
                        }
                        pendingDelayMillis = normalizedDelayMillis(delayHundredths)
                    } else if (!cursor.skipSubBlocks()) {
                        return rejected(MalformedReason)
                    }
                }

                ImageDescriptor -> {
                    val left = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
                    val top = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
                    val frameWidth = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
                    val frameHeight = cursor.readLittleEndian16() ?: return rejected(MalformedReason)
                    val imagePacked = cursor.readUnsignedByte() ?: return rejected(MalformedReason)
                    if (
                        frameWidth <= 0 ||
                        frameHeight <= 0 ||
                        left.toLong() + frameWidth > width ||
                        top.toLong() + frameHeight > height
                    ) {
                        return rejected(UnsafeFrameBoundsReason)
                    }
                    if (
                        hasColorTable(imagePacked) &&
                        !cursor.skip(colorTableByteCount(imagePacked))
                    ) {
                        return rejected(MalformedReason)
                    }
                    val minimumCodeSize = cursor.readUnsignedByte() ?: return rejected(MalformedReason)
                    if (minimumCodeSize !in MinimumLzwCodeSize..MaximumLzwCodeSize) {
                        return rejected(MalformedReason)
                    }
                    if (!cursor.skipSubBlocks()) return rejected(MalformedReason)
                    if (frames.size >= MaximumFrameCount) return rejected(TooManyFramesReason)
                    val nextElapsed = elapsedMillis.toLong() + pendingDelayMillis
                    if (nextElapsed > MaximumDurationMillis) return rejected(DurationTooLongReason)
                    frames +=
                        DagGifFrame(
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

        if (!trailerSeen || frames.isEmpty()) return rejected(MalformedReason)
        if (frames.size == 1) return DagGifTimelineResult.StaticGif
        return DagGifTimelineResult.Animated(
            DagGifTimeline(
                width = width,
                height = height,
                durationMillis = elapsedMillis,
                frames = frames.toList(),
            ),
        )
    }

    private fun hasGifSignature(bytes: ByteArray): Boolean {
        if (bytes.size < MinimumGifByteCount) return false
        return GifSignatures.any { signature ->
            signature.indices.all { index -> bytes[index] == signature[index].code.toByte() }
        }
    }

    private fun hasColorTable(packed: Int): Boolean = packed and ColorTableFlag != 0

    private fun colorTableByteCount(packed: Int): Int =
        ColorTableChannels * (1 shl ((packed and ColorTableSizeMask) + 1))

    private fun normalizedDelayMillis(delayHundredths: Int): Int =
        if (delayHundredths < MinimumReliableDelayHundredths) {
            DefaultFrameDurationMillis
        } else {
            delayHundredths * HundredthMillis
        }

    private fun rejected(reason: String) = DagGifTimelineResult.Rejected(reason)

    private class Cursor(
        private val bytes: ByteArray,
        private var index: Int,
    ) {
        fun hasRemaining(): Boolean = index < bytes.size

        fun readUnsignedByte(): Int? = if (index < bytes.size) bytes[index++].toInt() and 0xff else null

        fun readLittleEndian16(): Int? {
            val low = readUnsignedByte() ?: return null
            val high = readUnsignedByte() ?: return null
            return low or (high shl 8)
        }

        fun skip(count: Int): Boolean {
            if (count < 0 || count > bytes.size - index) return false
            index += count
            return true
        }

        fun skipSubBlocks(): Boolean {
            while (true) {
                val size = readUnsignedByte() ?: return false
                if (size == 0) return true
                if (!skip(size)) return false
            }
        }
    }

    const val MaximumFrameCount = 120
    const val MaximumDurationMillis = 60_000
    const val DefaultFrameDurationMillis = 100
    const val MalformedReason = "gif_malformed"
    const val ResourceTooLargeReason = "gif_too_large"
    const val UnsafeDimensionsReason = "gif_dimensions"
    const val UnsafeFrameBoundsReason = "gif_frame_bounds"
    const val TooManyFramesReason = "gif_frame_limit"
    const val DurationTooLongReason = "gif_duration_limit"

    private val GifSignatures = listOf("GIF87a", "GIF89a")
    private const val GifHeaderLength = 6
    private const val MinimumGifByteCount = 14
    private const val ColorTableFlag = 0x80
    private const val ColorTableSizeMask = 0x07
    private const val ColorTableChannels = 3
    private const val ExtensionIntroducer = 0x21
    private const val GraphicControlLabel = 0xf9
    private const val GraphicControlBlockSize = 4
    private const val ImageDescriptor = 0x2c
    private const val Trailer = 0x3b
    private const val MinimumLzwCodeSize = 2
    private const val MaximumLzwCodeSize = 8
    private const val MinimumReliableDelayHundredths = 2
    private const val HundredthMillis = 10
}
