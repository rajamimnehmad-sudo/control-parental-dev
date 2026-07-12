package com.contentfilter.feature.vpn.domainlist

import java.nio.ByteBuffer
import java.nio.ByteOrder

interface DynamicDomainBlocklist {
    fun categoryFor(domain: String): String?
}

class WebDomainList private constructor(
    val version: Long,
    private val adult: BloomFilter,
    private val mixedAdult: BloomFilter,
    private val educationalExceptions: Set<String>,
    private val devCanaries: Set<String>,
) : DynamicDomainBlocklist {
    override fun categoryFor(domain: String): String? {
        val candidates = domain.normalizedDomainCandidates()
        if (candidates.any(educationalExceptions::contains)) return null
        if (candidates.any(devCanaries::contains)) return CategoryDevCanary
        if (candidates.any(mixedAdult::mightContain)) return CategoryMixedAdult
        if (candidates.any(adult::mightContain)) return CategoryAdult
        return null
    }

    companion object {
        const val CategoryAdult = "adult"
        const val CategoryMixedAdult = "mixed_adult"
        const val CategoryDevCanary = "dev_test_blocked"
        const val FormatVersion = 1
        const val HeaderSize = 48
        val Magic = "CFDL0001".encodeToByteArray()

        fun parse(bytes: ByteArray): WebDomainList {
            require(bytes.size >= HeaderSize) { "Domain list header is incomplete." }
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(Magic.size).also(header::get)
            require(magic.contentEquals(Magic)) { "Unsupported domain list magic." }
            val version = header.long
            require(header.int == FormatVersion) { "Unsupported domain list format." }
            val hashCount = header.int
            val adultBitCount = header.int
            val mixedBitCount = header.int
            header.int // adult count is manifest metadata
            header.int // mixed adult count is manifest metadata
            val exceptionLength = header.int
            val canaryLength = header.int
            require(hashCount in 1..16 && adultBitCount > 0 && mixedBitCount > 0)
            val adultByteCount = adultBitCount.bytesForBits()
            val mixedByteCount = mixedBitCount.bytesForBits()
            val expectedSize = HeaderSize + adultByteCount + mixedByteCount + exceptionLength + canaryLength
            require(expectedSize == bytes.size) { "Domain list size does not match its header." }
            var offset = HeaderSize
            val adultBits = bytes.copyOfRange(offset, offset + adultByteCount)
            offset += adultByteCount
            val mixedBits = bytes.copyOfRange(offset, offset + mixedByteCount)
            offset += mixedByteCount
            val exceptions = bytes.decodeLines(offset, exceptionLength)
            offset += exceptionLength
            val canaries = bytes.decodeLines(offset, canaryLength)
            return WebDomainList(
                version = version,
                adult = BloomFilter(adultBits, adultBitCount, hashCount),
                mixedAdult = BloomFilter(mixedBits, mixedBitCount, hashCount),
                educationalExceptions = exceptions,
                devCanaries = canaries,
            )
        }

        private fun Int.bytesForBits(): Int = (this + 7) / 8

        private fun ByteArray.decodeLines(
            offset: Int,
            length: Int,
        ): Set<String> =
            if (length == 0) {
                emptySet()
            } else {
                decodeToString(offset, offset + length)
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
            }
    }
}

internal class BloomFilter(
    private val bits: ByteArray,
    private val bitCount: Int,
    private val hashCount: Int,
) {
    fun mightContain(value: String): Boolean {
        val encoded = value.encodeToByteArray()
        val first = fnv1a(encoded, FirstSeed)
        val second = fnv1a(encoded, SecondSeed) or 1
        repeat(hashCount) { index ->
            val combined = first.toUInt().toLong() + index.toLong() * second.toUInt().toLong()
            val bitIndex = (combined % bitCount).toInt()
            val mask = 1 shl (bitIndex and 7)
            if ((bits[bitIndex ushr 3].toInt() and mask) == 0) return false
        }
        return true
    }

    companion object {
        const val FirstSeed = -2_126_354_547
        const val SecondSeed = 16_777_619

        fun indexes(
            value: String,
            bitCount: Int,
            hashCount: Int,
        ): IntArray {
            val encoded = value.encodeToByteArray()
            val first = fnv1a(encoded, FirstSeed)
            val second = fnv1a(encoded, SecondSeed) or 1
            return IntArray(hashCount) { index ->
                val combined = first.toUInt().toLong() + index.toLong() * second.toUInt().toLong()
                (combined % bitCount).toInt()
            }
        }

        private fun fnv1a(
            bytes: ByteArray,
            seed: Int,
        ): Int {
            var hash = seed
            bytes.forEach { byte ->
                hash = hash xor (byte.toInt() and 0xff)
                hash *= 16_777_619
            }
            return hash
        }
    }
}

private fun String.normalizedDomainCandidates(): List<String> {
    val normalized = trim().lowercase().removeSuffix(".").removePrefix("www.")
    if (normalized.isBlank()) return emptyList()
    val labels = normalized.split('.')
    return labels.indices
        .asSequence()
        .map { labels.drop(it).joinToString(".") }
        .filter { it.contains('.') }
        .toList()
}
