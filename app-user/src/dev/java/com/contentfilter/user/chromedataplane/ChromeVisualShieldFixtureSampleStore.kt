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
    Flickr01(
        wireName = "flickr-01",
        expectedSha256 = "cf08dfa8750db0859349d811f47248db659f2d7770e3985a651c09425b81d847",
        expectedBytes = 3_403_942,
        sourceUrl = "https://farm6.staticflickr.com/5822/20582092196_9d95b6f648_o.jpg",
    ),
    Flickr02(
        wireName = "flickr-02",
        expectedSha256 = "f621c6807d7449f9696497fcc7050d72477679dc1fc5f0580964f3d34a559717",
        expectedBytes = 837_774,
        sourceUrl = "https://farm6.staticflickr.com/5230/5638781189_0e6fce455f_o.jpg",
    ),
    Flickr03(
        wireName = "flickr-03",
        expectedSha256 = "5033e784e2ab20fe2ca2afdaf5715203f91fc0dddde8114e970f0cb815c3f839",
        expectedBytes = 1_452_444,
        sourceUrl = "https://farm6.staticflickr.com/4151/5054191013_66512b5c4c_o.jpg",
    ),
    Flickr04(
        wireName = "flickr-04",
        expectedSha256 = "8e3b727818b8238247be1ba06c50a4c9083ae9cc50a5c59d0441f50bdb423266",
        expectedBytes = 2_372_694,
        sourceUrl = "https://farm6.staticflickr.com/3103/2382183276_3318f8e85f_o.jpg",
    ),
    Flickr05(
        wireName = "flickr-05",
        expectedSha256 = "7b7c170b59b7801af982b8b9019c758fe3f092416589c2588913fe2c37e80ae4",
        expectedBytes = 2_965_483,
        sourceUrl = "https://farm6.staticflickr.com/2552/3851641637_6be328885c_o.jpg",
    ),
    Flickr06(
        wireName = "flickr-06",
        expectedSha256 = "399b9608f1b7932f17856736900d59072ba786f8c21a577a57c61f069292e09e",
        expectedBytes = 421_667,
        sourceUrl = "https://farm6.staticflickr.com/41/85785791_72010e47eb_o.jpg",
    ),
    Flickr07(
        wireName = "flickr-07",
        expectedSha256 = "29dec5f20b7fe11cdf316e3181d9441d959d77d68c2673401bfb2508931c0efe",
        expectedBytes = 2_916_926,
        sourceUrl = "https://farm6.staticflickr.com/3850/14340510738_fa7c27b4e1_o.jpg",
    ),
    Flickr08(
        wireName = "flickr-08",
        expectedSha256 = "68971d61fee132615824d4c4ddbc7fcd3b8fdec92a3ca58a00bfc3363ef8e77d",
        expectedBytes = 3_808_825,
        sourceUrl = "https://farm6.staticflickr.com/2926/14054216649_855e7f912b_o.jpg",
    ),
    Flickr09(
        wireName = "flickr-09",
        expectedSha256 = "1eb569d6ac5f68fbac134f39cbcef273b45b3bb0ca7e84851f3586997a4c75e0",
        expectedBytes = 2_510_836,
        sourceUrl = "https://farm6.staticflickr.com/210/474180770_15c72a6696_o.jpg",
    ),
    Flickr10(
        wireName = "flickr-10",
        expectedSha256 = "e511025c0e181ea9b07812563b19f6c0d4d3e95dce9937d64fa445589e85adbd",
        expectedBytes = 77_187,
        sourceUrl = "https://farm6.staticflickr.com/5600/15526796846_f43d9eb869_o.jpg",
    ),
    Flickr11(
        wireName = "flickr-11",
        expectedSha256 = "25e28282ebe46c982beccbd7b951bf2c4813f40264f1d4248f74b94ee1d7c56f",
        expectedBytes = 645_604,
        sourceUrl = "https://farm6.staticflickr.com/3560/3469462979_ccc4840905_o.jpg",
    ),
    Flickr12(
        wireName = "flickr-12",
        expectedSha256 = "7b0a2135420840d25a1892354145c6cb873b79ea8a15828c8d6f25594c57b8da",
        expectedBytes = 1_434_074,
        sourceUrl = "https://farm6.staticflickr.com/1132/1306825778_63caee2b0a_o.jpg",
    ),
    Flickr13(
        wireName = "flickr-13",
        expectedSha256 = "8f4fce3d75affcc60758cd0730a76dcd8fc42e76c0558c4215e3350229a0882f",
        expectedBytes = 153_139,
        sourceUrl = "https://farm6.staticflickr.com/3690/12022741784_9f8f0abc1e_o.jpg",
    ),
    Flickr14(
        wireName = "flickr-14",
        expectedSha256 = "35cd811a093e1e04cef0913663e8abbac866ded30169d42a2c24b9606c842653",
        expectedBytes = 25_845,
        sourceUrl = "https://farm6.staticflickr.com/3256/2858049912_ef32c5bc5f_o.jpg",
    ),
    Flickr15(
        wireName = "flickr-15",
        expectedSha256 = "1ce753d1c266ffbb36bc9be3e3215a99cd620d4f4ef0eb5e88230ab910381329",
        expectedBytes = 1_652_971,
        sourceUrl = "https://farm6.staticflickr.com/3501/4069272516_1f0bdff9f8_o.jpg",
    ),
    Flickr16(
        wireName = "flickr-16",
        expectedSha256 = "4658eb4cc073ef74bc6fcdaba5b6ffa2426aa8222137422705a291f5c2e70715",
        expectedBytes = 196_963,
        sourceUrl = "https://farm6.staticflickr.com/5236/5829923957_5045aba7f4_o.jpg",
    ),
    Block(
        wireName = "block",
        expectedSha256 = "9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94",
        expectedBytes = 146_249,
        sourceUrl = "https://farm6.staticflickr.com/3200/2970012318_98f7c80583_o.jpg",
    ),
    ;

    companion object {
        val renderedMatrix: List<ChromeVisualShieldFixtureSample> =
            entries.filter { it != Safe }

        fun fromWireName(value: String?): ChromeVisualShieldFixtureSample? =
            entries.firstOrNull { it.wireName == value }
    }
}

/** RAM-only loader for immutable public R3.1 gate samples. */
internal object ChromeVisualShieldFixtureSampleStore {
    private const val MaxEncodedChunkChars = 24_576

    private val staging = mutableMapOf<ChromeVisualShieldFixtureSample, WipeableBuffer>()
    private val verified = mutableMapOf<ChromeVisualShieldFixtureSample, ByteArray>()

    @Synchronized
    fun reset(sample: ChromeVisualShieldFixtureSample): String {
        ChromeVisualShieldRenderAttestationStore.clear(sample)
        ChromeVisualShieldRegionDiscoveryAttestationStore.clear()
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
    fun isReady(sample: ChromeVisualShieldFixtureSample): Boolean = sample in verified

    @Synchronized
    fun clear() {
        ChromeVisualShieldRenderAttestationStore.clear()
        ChromeVisualShieldRegionDiscoveryAttestationStore.clear()
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
