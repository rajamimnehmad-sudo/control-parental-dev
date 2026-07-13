package com.contentfilter.feature.vpn.domainlist

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

interface DynamicDomainBlocklist {
    fun categoryFor(domain: String): String?
}

class WebDomainList private constructor(
    val version: Long,
    private val categories: List<CategoryIndex>,
    private val educationalExceptions: Set<String>,
    private val devCanaries: Set<String>,
) : DynamicDomainBlocklist {
    val canaryIncluded: Boolean
        get() = devCanaries.isNotEmpty()

    override fun categoryFor(domain: String): String? {
        val candidates = domain.normalizedDomainCandidates()
        if (candidates.any(educationalExceptions::contains)) return null
        if (candidates.any(devCanaries::contains)) return CategoryDevCanary
        categories.forEach { category ->
            if (candidates.any { category.bloom.mightContain(it) && category.exact?.contains(it) == true }) {
                return category.name
            }
        }
        return null
    }

    companion object {
        const val CategoryAdult = "adult"
        const val CategoryMixedAdult = "mixed_adult"
        const val CategoryGambling = "gambling"
        const val CategoryDrugs = "drugs"
        const val CategoryPiracyTorrents = "piracy_torrents"
        const val CategoryDevCanary = "dev_test_blocked"
        const val FormatVersion = 3
        const val ExactFormatVersion = 2
        const val LegacyFormatVersion = 1
        const val LegacyHeaderSize = 48
        const val ExactHeaderSize = 56
        const val HeaderSize = 36
        const val CategoryDescriptorSize = 16
        const val ExactHashSize = 8
        val Magic = "CFDL0001".encodeToByteArray()

        fun parse(bytes: ByteArray): WebDomainList {
            require(bytes.size >= HeaderSize) { "Domain list header is incomplete." }
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(Magic.size).also(header::get)
            require(magic.contentEquals(Magic)) { "Unsupported domain list magic." }
            val version = header.long
            val formatVersion = header.int
            require(formatVersion in LegacyFormatVersion..FormatVersion) {
                "Unsupported domain list format."
            }
            return if (formatVersion == FormatVersion) {
                parseCurrent(bytes, header, version)
            } else {
                require(formatVersion == LegacyFormatVersion || formatVersion == ExactFormatVersion) {
                    "Unsupported domain list format."
                }
                parseLegacy(bytes, header, version, formatVersion)
            }
        }

        private fun parseCurrent(
            bytes: ByteArray,
            header: ByteBuffer,
            version: Long,
        ): WebDomainList {
            require(bytes.size >= HeaderSize) { "Domain list header is incomplete." }
            val hashCount = header.int
            val categoryCount = header.int
            val exceptionLength = header.int
            val canaryLength = header.int
            require(hashCount in 1..16 && categoryCount in 1..16 && exceptionLength >= 0 && canaryLength >= 0)
            require(bytes.size >= HeaderSize + categoryCount * CategoryDescriptorSize)
            val descriptors =
                List(categoryCount) {
                    CategoryDescriptor(header.int, header.int, header.int, header.int)
                }
            descriptors.forEach {
                require(it.nameLength in 1..64 && it.bitCount in 1..500_000_000 && it.entryCount >= 0)
                require(it.exactLength.toLong() == it.entryCount.toLong() * ExactHashSize)
            }
            val expectedSize =
                HeaderSize.toLong() + categoryCount.toLong() * CategoryDescriptorSize +
                    descriptors.sumOf { it.nameLength.toLong() + it.bitCount.bytesForBits().toLong() + it.exactLength } +
                    exceptionLength + canaryLength
            require(expectedSize == bytes.size.toLong()) { "Domain list size does not match its header." }
            var offset = HeaderSize + categoryCount * CategoryDescriptorSize
            val categories =
                descriptors.map { descriptor ->
                    val name = bytes.decodeToString(offset, offset + descriptor.nameLength)
                    require(name in SupportedCategories) { "Unsupported domain list category." }
                    offset += descriptor.nameLength
                    val bloomLength = descriptor.bitCount.bytesForBits()
                    val bloom =
                        BloomFilter(bytes.copyOfRange(offset, offset + bloomLength), descriptor.bitCount, hashCount)
                    offset += bloomLength
                    val exact = ExactHashIndex(bytes.copyOfRange(offset, offset + descriptor.exactLength))
                    offset += descriptor.exactLength
                    CategoryIndex(name, bloom, exact)
                }
            require(categories.map { it.name }.distinct().size == categories.size)
            val exceptions = bytes.decodeLines(offset, exceptionLength)
            offset += exceptionLength
            val canaries = bytes.decodeLines(offset, canaryLength)
            return WebDomainList(version, categories, exceptions, canaries)
        }

        private fun parseLegacy(
            bytes: ByteArray,
            header: ByteBuffer,
            version: Long,
            formatVersion: Int,
        ): WebDomainList {
            val hashCount = header.int
            val adultBitCount = header.int
            val mixedBitCount = header.int
            val adultCount = header.int
            val mixedCount = header.int
            val adultExactLength = if (formatVersion == ExactFormatVersion) header.int else 0
            val mixedExactLength = if (formatVersion == ExactFormatVersion) header.int else 0
            val exceptionLength = header.int
            val canaryLength = header.int
            require(hashCount in 1..16 && adultBitCount > 0 && mixedBitCount > 0)
            val adultByteCount = adultBitCount.bytesForBits()
            val mixedByteCount = mixedBitCount.bytesForBits()
            if (formatVersion == ExactFormatVersion) {
                require(adultExactLength == adultCount * ExactHashSize)
                require(mixedExactLength == mixedCount * ExactHashSize)
            }
            val headerSize = if (formatVersion == ExactFormatVersion) ExactHeaderSize else LegacyHeaderSize
            val expectedSize =
                headerSize.toLong() + adultByteCount + mixedByteCount + adultExactLength +
                    mixedExactLength + exceptionLength + canaryLength
            require(expectedSize == bytes.size.toLong()) { "Domain list size does not match its header." }
            var offset = headerSize
            val adultBits = bytes.copyOfRange(offset, offset + adultByteCount)
            offset += adultByteCount
            val mixedBits = bytes.copyOfRange(offset, offset + mixedByteCount)
            offset += mixedByteCount
            val exactAdult =
                adultExactLength.takeIf { it > 0 }?.let {
                    ExactHashIndex(bytes.copyOfRange(offset, offset + it)).also { offset += adultExactLength }
                }
            val exactMixed =
                mixedExactLength.takeIf { it > 0 }?.let {
                    ExactHashIndex(bytes.copyOfRange(offset, offset + it)).also { offset += mixedExactLength }
                }
            val exceptions = bytes.decodeLines(offset, exceptionLength)
            offset += exceptionLength
            val canaries = bytes.decodeLines(offset, canaryLength)
            return WebDomainList(
                version = version,
                categories =
                    listOf(
                        CategoryIndex(CategoryMixedAdult, BloomFilter(mixedBits, mixedBitCount, hashCount), exactMixed),
                        CategoryIndex(CategoryAdult, BloomFilter(adultBits, adultBitCount, hashCount), exactAdult),
                    ),
                educationalExceptions = exceptions,
                devCanaries = canaries,
            )
        }

        private fun Int.bytesForBits(): Int = (this + 7) / 8

        private val SupportedCategories =
            setOf(CategoryAdult, CategoryMixedAdult, CategoryGambling, CategoryDrugs, CategoryPiracyTorrents)

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

private data class CategoryDescriptor(val nameLength: Int, val bitCount: Int, val entryCount: Int, val exactLength: Int)

private data class CategoryIndex(val name: String, val bloom: BloomFilter, val exact: ExactHashIndex?)

internal class ExactHashIndex(
    private val hashes: ByteArray,
) {
    init {
        require(hashes.size % WebDomainList.ExactHashSize == 0)
    }

    fun contains(value: String): Boolean {
        val target = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
        var low = 0
        var high = hashes.size / WebDomainList.ExactHashSize - 1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = compareAt(middle, target)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return true
            }
        }
        return false
    }

    private fun compareAt(
        index: Int,
        target: ByteArray,
    ): Int {
        val offset = index * WebDomainList.ExactHashSize
        repeat(WebDomainList.ExactHashSize) { byteIndex ->
            val stored = hashes[offset + byteIndex].toInt() and 0xff
            val expected = target[byteIndex].toInt() and 0xff
            if (stored != expected) return stored - expected
        }
        return 0
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
        const val FirstSeed = -2_128_831_035
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
