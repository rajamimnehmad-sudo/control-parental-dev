package com.contentfilter.feature.vpn.dns

import java.net.InetAddress

internal data class DnsAnswerAddresses(
    val addresses: Set<InetAddress>,
    val ttlMillis: Long,
)

/** Extracts only A/AAAA data and its shortest TTL from a validated DNS response. */
internal object DnsAnswerAddressParser {
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun parse(response: ByteArray): DnsAnswerAddresses? {
        if (response.size < DnsHeaderSize) return null
        val questionCount = response.readUInt16(QuestionCountOffset)
        val recordCount =
            response.readUInt16(AnswerCountOffset) +
                response.readUInt16(AuthorityCountOffset) +
                response.readUInt16(AdditionalCountOffset)
        var cursor = DnsHeaderSize
        repeat(questionCount) {
            cursor = response.skipName(cursor) ?: return null
            if (cursor + QuestionTrailerSize > response.size) return null
            cursor += QuestionTrailerSize
        }
        val addresses = linkedSetOf<InetAddress>()
        var shortestTtlSeconds: Long? = null
        repeat(recordCount) {
            cursor = response.skipName(cursor) ?: return null
            if (cursor + RecordHeaderSize > response.size) return null
            val type = response.readUInt16(cursor)
            val recordClass = response.readUInt16(cursor + ClassOffset)
            val ttlSeconds = response.readUInt32(cursor + TtlOffset)
            val dataLength = response.readUInt16(cursor + DataLengthOffset)
            cursor += RecordHeaderSize
            if (cursor + dataLength > response.size) return null
            val expectedLength =
                when (type) {
                    AddressTypeIpv4 -> Ipv4AddressLength
                    AddressTypeIpv6 -> Ipv6AddressLength
                    else -> null
                }
            if (recordClass == InternetClass && expectedLength == dataLength) {
                val address = InetAddress.getByAddress(response.copyOfRange(cursor, cursor + dataLength))
                if (!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress) {
                    addresses += address
                    shortestTtlSeconds = minOf(shortestTtlSeconds ?: ttlSeconds, ttlSeconds)
                }
            }
            cursor += dataLength
        }
        if (addresses.isEmpty()) return null
        return DnsAnswerAddresses(
            addresses = addresses,
            ttlMillis = (shortestTtlSeconds ?: 0L) * MillisPerSecond,
        )
    }

    @Suppress("ReturnCount")
    private fun ByteArray.skipName(start: Int): Int? {
        var cursor = start
        while (cursor < size) {
            val length = this[cursor].toInt() and ByteMask
            when {
                length == 0 -> return cursor + 1
                length and PointerMask == PointerMask -> return (cursor + PointerLength).takeIf { it <= size }
                length > MaxLabelLength || cursor + 1 + length > size -> return null
                else -> cursor += 1 + length
            }
        }
        return null
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        ((this[offset].toInt() and ByteMask) shl ByteSizeBits) or
            (this[offset + 1].toInt() and ByteMask)

    private fun ByteArray.readUInt32(offset: Int): Long =
        ((this[offset].toLong() and ByteMaskLong) shl 24) or
            ((this[offset + 1].toLong() and ByteMaskLong) shl 16) or
            ((this[offset + 2].toLong() and ByteMaskLong) shl 8) or
            (this[offset + 3].toLong() and ByteMaskLong)

    private const val DnsHeaderSize = 12
    private const val QuestionCountOffset = 4
    private const val AnswerCountOffset = 6
    private const val AuthorityCountOffset = 8
    private const val AdditionalCountOffset = 10
    private const val QuestionTrailerSize = 4
    private const val RecordHeaderSize = 10
    private const val ClassOffset = 2
    private const val TtlOffset = 4
    private const val DataLengthOffset = 8
    private const val InternetClass = 1
    private const val AddressTypeIpv4 = 1
    private const val AddressTypeIpv6 = 28
    private const val Ipv4AddressLength = 4
    private const val Ipv6AddressLength = 16
    private const val PointerMask = 0xC0
    private const val PointerLength = 2
    private const val MaxLabelLength = 63
    private const val ByteMask = 0xFF
    private const val ByteMaskLong = 0xFFL
    private const val ByteSizeBits = 8
    private const val MillisPerSecond = 1_000L
}
