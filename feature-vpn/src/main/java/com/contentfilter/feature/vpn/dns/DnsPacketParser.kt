package com.contentfilter.feature.vpn.dns

/**
 * Minimal parser for IPv4 + UDP + traditional DNS query packets.
 */
class DnsPacketParser {
    fun parseQuery(
        packet: ByteArray,
        length: Int,
    ): DnsQuestion? {
        if (length < MIN_IPV4_UDP_DNS_SIZE || packet[0].highNibble() != IPV4_VERSION) return null
        val ipHeaderLength = (packet[0].toInt() and IPV4_HEADER_MASK) * INT_SIZE_BYTES
        if (ipHeaderLength < MIN_IPV4_HEADER_SIZE || length < ipHeaderLength + UDP_HEADER_SIZE + DNS_HEADER_SIZE) {
            return null
        }
        if ((packet[IP_PROTOCOL_OFFSET].toInt() and BYTE_MASK) != UDP_PROTOCOL) return null

        val udpOffset = ipHeaderLength
        val sourcePort = readUInt16(packet, udpOffset)
        val destinationPort = readUInt16(packet, udpOffset + PORT_FIELD_SIZE)
        if (destinationPort != DNS_PORT) return null
        val udpLength = readUInt16(packet, udpOffset + UDP_LENGTH_OFFSET)
        if (udpLength < UDP_HEADER_SIZE + DNS_HEADER_SIZE || udpOffset + udpLength > length) return null

        val dnsOffset = udpOffset + UDP_HEADER_SIZE
        val dnsLength = udpLength - UDP_HEADER_SIZE
        if (dnsLength < DNS_HEADER_SIZE || readUInt16(packet, dnsOffset + DNS_QDCOUNT_OFFSET) < 1) return null

        val transactionId = readUInt16(packet, dnsOffset)
        val labels = mutableListOf<String>()
        var cursor = dnsOffset + DNS_HEADER_SIZE

        while (cursor < length) {
            val labelLength = packet[cursor].toInt() and BYTE_MASK
            if (labelLength == 0) {
                cursor += 1
                break
            }
            if (labelLength and DNS_POINTER_MASK == DNS_POINTER_MASK) return null
            if (labelLength > MAX_LABEL_LENGTH || cursor + 1 + labelLength > length) return null

            val label = packet.decodeAsciiLabel(cursor + 1, labelLength) ?: return null
            if (!label.isValidDomainLabel()) return null
            labels += label.lowercase()
            cursor += 1 + labelLength
        }

        val dnsEnd = dnsOffset + dnsLength
        if (labels.isEmpty() || cursor + QUESTION_TRAILER_SIZE > dnsEnd) return null
        val domain = labels.joinToString(".")
        if (!domain.isValidDomain()) return null

        val type = readUInt16(packet, cursor)
        val queryPayload = packet.copyOfRange(dnsOffset, dnsEnd)
        return DnsQuestion(
            transactionId = transactionId,
            domain = domain,
            type = type,
            sourceAddress = packet.copyOfRange(IP_SOURCE_OFFSET, IP_SOURCE_OFFSET + IPV4_ADDRESS_SIZE),
            destinationAddress = packet.copyOfRange(IP_DESTINATION_OFFSET, IP_DESTINATION_OFFSET + IPV4_ADDRESS_SIZE),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            queryPayload = queryPayload,
        )
    }

    private fun Byte.highNibble(): Int = (toInt() ushr NIBBLE_SHIFT) and IPV4_HEADER_MASK

    private fun String.isValidDomain(): Boolean =
        length <= MAX_DOMAIN_LENGTH && split(".").all { it.isValidDomainLabel() }

    private fun String.isValidDomainLabel(): Boolean =
        isNotBlank() &&
            length <= MAX_LABEL_LENGTH &&
            first() != '-' &&
            last() != '-' &&
            all { it.isLetterOrDigit() || it == '-' }

    private fun ByteArray.decodeAsciiLabel(
        offset: Int,
        length: Int,
    ): String? {
        val chars = CharArray(length)
        repeat(length) { index ->
            val value = this[offset + index].toInt() and BYTE_MASK
            if (value !in ASCII_PRINTABLE_RANGE) return null
            chars[index] = value.toChar()
        }
        return chars.concatToString()
    }

    private fun readUInt16(
        data: ByteArray,
        offset: Int,
    ): Int =
        ((data[offset].toInt() and BYTE_MASK) shl BYTE_SIZE_BITS) or
            (data[offset + 1].toInt() and BYTE_MASK)

    private companion object {
        const val BYTE_MASK = 0xFF
        const val BYTE_SIZE_BITS = 8
        val ASCII_PRINTABLE_RANGE = 0x21..0x7E
        const val DNS_PORT = 53
        const val DNS_HEADER_SIZE = 12
        const val DNS_POINTER_MASK = 0xC0
        const val DNS_QDCOUNT_OFFSET = 4
        const val INT_SIZE_BYTES = 4
        const val IP_DESTINATION_OFFSET = 16
        const val IP_PROTOCOL_OFFSET = 9
        const val IP_SOURCE_OFFSET = 12
        const val IPV4_ADDRESS_SIZE = 4
        const val IPV4_HEADER_MASK = 0x0F
        const val IPV4_VERSION = 4
        const val MAX_DOMAIN_LENGTH = 253
        const val MAX_LABEL_LENGTH = 63
        const val MIN_IPV4_HEADER_SIZE = 20
        const val MIN_IPV4_UDP_DNS_SIZE = 40
        const val NIBBLE_SHIFT = 4
        const val PORT_FIELD_SIZE = 2
        const val QUESTION_TRAILER_SIZE = 4
        const val UDP_HEADER_SIZE = 8
        const val UDP_LENGTH_OFFSET = 4
        const val UDP_PROTOCOL = 17
    }
}
