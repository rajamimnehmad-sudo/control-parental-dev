package com.contentfilter.user.chromedataplane

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Samples clear MP4 frames through the existing R3.1 decision session before release. */
internal class ChromeMediaVideoPayloadInspector(
    private val decisionSession: ChromePhotoDecisionSession,
    private val maximumSamples: Int = 3,
) : ChromeMediaPayloadInspector {
    init {
        require(maximumSamples in 1..8)
    }

    override fun inspect(
        bytes: ByteArray,
        declaredMimeType: String,
    ): ChromeMediaPayloadDecision {
        val mime = declaredMimeType.normalizedImageMimeType()
        if (mime !in SupportedVideoMimeTypes) {
            return ChromeMediaPayloadDecision.Unknown("video_mime_unsupported")
        }
        return if (mime in IsoBmffVideoMimeTypes) {
            when (ChromeIsoBmffContainer.inspect(bytes)) {
                ChromeIsoBmffDecision.Encrypted -> ChromeMediaPayloadDecision.Unknown("encrypted_media_not_inspectable")
                ChromeIsoBmffDecision.Unsupported -> ChromeMediaPayloadDecision.Unknown("video_container_unsupported")
                ChromeIsoBmffDecision.ClearMp4 -> inspectFrames(bytes)
            }
        } else {
            inspectFrames(bytes)
        }
    }

    private fun inspectFrames(bytes: ByteArray): ChromeMediaPayloadDecision {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ByteArrayMediaDataSource(bytes))
            val durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val timestamps =
                listOfNotNull(
                    0L,
                    durationMillis?.div(2),
                    durationMillis?.minus(1L)?.coerceAtLeast(0L),
                ).distinct().take(maximumSamples)
            if (timestamps.isEmpty()) return ChromeMediaPayloadDecision.Unknown("video_duration_unavailable")
            var decoded = 0
            for (timestamp in timestamps) {
                val frame =
                    runCatching {
                        retriever.getScaledFrameAtTime(
                            timestamp * 1_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            SampleEdge,
                            SampleEdge,
                        )
                    }.getOrNull() ?: return ChromeMediaPayloadDecision.Unknown("video_frame_decode_failed")
                decoded++
                val decision = inspectFrame(frame)
                frame.recycle()
                when (decision) {
                    ChromeMediaPayloadDecision.Safe -> Unit
                    else -> return decision
                }
            }
            if (decoded == 0) ChromeMediaPayloadDecision.Unknown("video_without_decodable_frame") else ChromeMediaPayloadDecision.Safe
        } catch (_: Throwable) {
            ChromeMediaPayloadDecision.Unknown("video_decode_exception")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun inspectFrame(frame: Bitmap): ChromeMediaPayloadDecision {
        val output = ByteArrayOutputStream()
        return try {
            if (!frame.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return ChromeMediaPayloadDecision.Unknown("video_frame_encode_failed")
            }
            val png = output.toByteArray()
            try {
                val result = decisionSession.decide(sha256(png), png, "image/png")
                when (result.decision) {
                    ChromePhotoDecision.Safe -> ChromeMediaPayloadDecision.Safe
                    ChromePhotoDecision.Block -> ChromeMediaPayloadDecision.Block
                    ChromePhotoDecision.Unknown -> ChromeMediaPayloadDecision.Unknown(result.reason)
                }
            } finally {
                png.fill(0)
            }
        } finally {
            output.reset()
        }
    }

    private companion object {
        const val SampleEdge = 512
        val IsoBmffVideoMimeTypes = setOf("video/mp4", "video/quicktime", "video/x-m4v")
        val SupportedVideoMimeTypes = IsoBmffVideoMimeTypes + setOf("video/mp2t", "video/mpeg", "application/mp2t")
    }
}

private class ByteArrayMediaDataSource(
    private val bytes: ByteArray,
) : MediaDataSource() {
    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        if (position < 0L || position >= bytes.size) return -1
        if (offset < 0 || size < 0 || offset > buffer.size - size) return -1
        val count = minOf(size, bytes.size - position.toInt())
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

private enum class ChromeIsoBmffDecision {
    ClearMp4,
    Encrypted,
    Unsupported,
}

private object ChromeIsoBmffContainer {
    private val SupportedBrands =
        setOf("isom", "iso2", "iso5", "iso6", "mp41", "mp42", "avc1", "hvc1", "hev1", "av01", "M4V ", "3gp4")
    private val EncryptedBoxes = setOf("pssh", "sinf", "encv", "enca", "schm", "tenc")

    fun inspect(bytes: ByteArray): ChromeIsoBmffDecision {
        var offset = 0
        var ftyp = false
        var moov = false
        var encrypted = false
        var boxes = 0
        while (offset + 8 <= bytes.size && boxes++ < 4096) {
            val size32 = readUInt32(bytes, offset) ?: return ChromeIsoBmffDecision.Unsupported
            val type = ascii(bytes, offset + 4, 4) ?: return ChromeIsoBmffDecision.Unsupported
            val headerSize: Long
            val boxSize: Long
            if (size32 == 1L) {
                if (offset + 16 > bytes.size) return ChromeIsoBmffDecision.Unsupported
                headerSize = 16L
                boxSize = readUInt64(bytes, offset + 8) ?: return ChromeIsoBmffDecision.Unsupported
            } else {
                headerSize = 8L
                boxSize = size32
            }
            if (boxSize == 0L) {
                if (type == "moov") moov = true
                break
            }
            if (boxSize < headerSize || boxSize > bytes.size - offset) return ChromeIsoBmffDecision.Unsupported
            if (type == "ftyp") {
                val major = ascii(bytes, offset + 8, 4)
                ftyp = major in SupportedBrands
            }
            if (type == "moov") moov = true
            if (type in EncryptedBoxes) encrypted = true
            offset += boxSize.toInt()
        }
        return when {
            encrypted -> ChromeIsoBmffDecision.Encrypted
            ftyp && moov -> ChromeIsoBmffDecision.ClearMp4
            else -> ChromeIsoBmffDecision.Unsupported
        }
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 8 > bytes.size) return null
        var value = 0L
        repeat(8) { index -> value = (value shl 8) or (bytes[offset + index].toLong() and 0xff) }
        return value.takeIf { it >= 0L }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String? =
        if (offset < 0 || offset + length > bytes.size) null else bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
}
