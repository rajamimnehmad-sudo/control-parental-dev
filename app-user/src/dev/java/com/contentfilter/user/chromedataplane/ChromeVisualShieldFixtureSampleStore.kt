package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

internal enum class ChromeVisualShieldFixtureSample(
    val wireName: String,
    val expectedSha256: String,
) {
    Safe(
        wireName = "safe",
        expectedSha256 = "541a1ef54364c3a8ac499fbc02bb6275c1a479e8b9bd2aed723f4518c44fd8c1",
    ),
    Block(
        wireName = "block",
        expectedSha256 = "4a5afeaf6c80b4393c590e1c000485faa47b86825738ac8d898159cccc361d00",
    ),
    ;

    companion object {
        fun fromWireName(value: String?): ChromeVisualShieldFixtureSample? =
            entries.firstOrNull { it.wireName == value }
    }
}

/** RAM-only loader: fixture image files remain outside Git and are supplied in bounded chunks. */
internal object ChromeVisualShieldFixtureSampleStore {
    private val staging = mutableMapOf<ChromeVisualShieldFixtureSample, ByteArrayOutputStream>()
    private val verified = mutableMapOf<ChromeVisualShieldFixtureSample, ByteArray>()

    @Synchronized
    fun reset(sample: ChromeVisualShieldFixtureSample): String {
        staging[sample] = ByteArrayOutputStream()
        verified.remove(sample)?.fill(0)
        return "result=fixture_reset sample=${sample.wireName}"
    }

    @Synchronized
    fun append(
        sample: ChromeVisualShieldFixtureSample,
        encodedChunk: String,
    ): String {
        val target = staging[sample] ?: return "result=fixture_not_reset sample=${sample.wireName}"
        val bytes =
            runCatching { Base64.getDecoder().decode(encodedChunk) }.getOrNull()
                ?: return "result=fixture_invalid_base64 sample=${sample.wireName}"
        target.write(bytes)
        bytes.fill(0)
        return "result=fixture_chunk sample=${sample.wireName} bytes=${target.size()}"
    }

    @Synchronized
    fun commit(sample: ChromeVisualShieldFixtureSample): String {
        val bytes =
            staging.remove(sample)?.toByteArray()
                ?: return "result=fixture_not_reset sample=${sample.wireName}"
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
        staging.values.forEach { buffer -> buffer.toByteArray().fill(0) }
        staging.clear()
        verified.values.forEach { bytes -> bytes.fill(0) }
        verified.clear()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
