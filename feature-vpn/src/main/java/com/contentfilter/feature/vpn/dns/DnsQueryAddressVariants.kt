package com.contentfilter.feature.vpn.dns

/** Builds A and AAAA variants of a validated traditional DNS question. */
internal object DnsQueryAddressVariants {
    @Suppress("ReturnCount")
    fun from(query: ByteArray): List<ByteArray> {
        if (query.size < DnsHeaderSize || query.readUInt16(QuestionCountOffset) != 1) return emptyList()
        var cursor = DnsHeaderSize
        while (cursor < query.size) {
            val length = query[cursor].toInt() and ByteMask
            if (length == 0) break
            if (length > MaxLabelLength || cursor + 1 + length > query.size) return emptyList()
            cursor += 1 + length
        }
        val typeOffset = cursor + 1
        if (typeOffset + QuestionTrailerSize > query.size) return emptyList()
        return listOf(AddressTypeIpv4, AddressTypeIpv6).map { type ->
            query.copyOf().also { variant -> variant.writeUInt16(typeOffset, type) }
        }
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        ((this[offset].toInt() and ByteMask) shl ByteSizeBits) or
            (this[offset + 1].toInt() and ByteMask)

    private fun ByteArray.writeUInt16(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr ByteSizeBits).toByte()
        this[offset + 1] = value.toByte()
    }

    private const val DnsHeaderSize = 12
    private const val QuestionCountOffset = 4
    private const val QuestionTrailerSize = 4
    private const val AddressTypeIpv4 = 1
    private const val AddressTypeIpv6 = 28
    private const val MaxLabelLength = 63
    private const val ByteMask = 0xFF
    private const val ByteSizeBits = 8
}
