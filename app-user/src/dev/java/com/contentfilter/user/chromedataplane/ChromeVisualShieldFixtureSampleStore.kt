package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

internal enum class ChromeVisualShieldFixtureSample(
    val wireName: String,
    val expectedSha256: String,
    val expectedBytes: Int,
    val sourceUrl: String,
) {
    Safe(
        wireName = "safe",
        expectedSha256 = "541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1",
        expectedBytes = 8_090,
        sourceUrl = "https://httpbingo.org/image/png",
    ),
    Block(
        wireName = "block",
        expectedSha256 = "9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94",
        expectedBytes = 146_249,
        sourceUrl = "https://farm6.staticflickr.com/3200/2970012318_98f7c80583_o.jpg",
    ),
    ;

    companion object {
        fun fromWireName(value: String?): ChromeVisualShieldFixtureSample? =
            entries.firstOrNull { it.wireName == value }
    }
}

/** RAM-only loader for the two immutable public R3.1 gate samples. */
internal object ChromeVisualShieldFixtureSampleStore {
    private const val MaxEncodedChunkChars = 24_576

    private val staging = mutableMapOf<ChromeVisualShieldFixtureSample, WipeableBuffer>()
    private val verified = mutableMapOf<ChromeVisualShieldFixtureSample, ByteArray>()

    @Synchronized
    fun reset(sample: ChromeVisualShieldFixtureSample): String {
        staging.put(sample, WipeableBuffer(sample.expectedBytes))?.wipe()
        verified.remove(sample)?.fill(0)
        return "result=fixture_reset sample=${sample.wireName}"
    }

    @Synchronized
    fun append(
        sample: ChromeVisualShieldFixtureSample,
        encodedChunk: String,
    ): String {
        val target = staging[sample] ?: return "result=fixture_not_reset sample=${sample.wireName}"
        if (encodedChunk.length > MaxEncodedChunkChars) {
            return "result=fixture_chunk_too_large sample=${sample.wireName} chars=${encodedChunk.length}"
        }
        val bytes =
            runCatching { Base64.getDecoder().decode(encodedChunk) }.getOrNull()
                ?: return "result=fixture_invalid_base64 sample=${sample.wireName}"
        try {
            if (target.size() + bytes.size > sample.expectedBytes) {
                return "result=fixture_size_overflow sample=${sample.wireName} bytes=${target.size() + bytes.size} expected=${sample.expectedBytes}"
            }
            target.write(bytes)
            return "result=fixture_chunk sample=${sample.wireName} bytes=${target.size()}"
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun commit(sample: ChromeVisualShieldFixtureSample): String {
        val target = staging.remove(sample) ?: return "result=fixture_not_reset sample=${sample.wireName}"
        val bytes = target.toByteArray()
        target.wipe()
        if (bytes.size != sample.expectedBytes) {
            val observedBytes = bytes.size
            bytes.fill(0)
            return "result=fixture_size_mismatch sample=${sample.wireName} observedBytes=$observedBytes expectedBytes=${sample.expectedBytes}"
        }
        val observed = sha256(bytes)
        if (observed != sample.expectedSha256) {
            bytes.fill(0)
            return "result=fixture_sha_mismatch sample=${sample.wireName} observedSha=$observed"
        }
        verified.put(sample, bytes)?.fill(0)
        return "result=fixture_ready sample=${sample.wireName} sha=$observed bytes=${bytes.size}"
    }

    @Synchronized
    fun payload(sample: ChromeVisualShieldFixtureSample): ByteArray? = verified[sample]?.copyOf()

    @Synchronized
    fun clear() {
        staging.values.forEach(WipeableBuffer::wipe)
        staging.clear()
        verified.values.forEach { bytes -> bytes.fill(0) }
        verified.clear()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private class WipeableBuffer(initialSize: Int) : ByteArrayOutputStream(initialSize) {
        fun wipe() {
            buf.fill(0)
            reset()
        }
    }
}
