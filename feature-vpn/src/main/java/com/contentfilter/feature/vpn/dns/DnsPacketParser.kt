package com.contentfilter.feature.vpn.dns

/**
 * Minimal parser for IPv4 + UDP + traditional DNS query packets.
 */
class DnsPacketParser {
    fun parseQuery(
        packet: ByteArray,
        length: Int,
    ): DnsQuestion? =
        when (val result = parse(packet, length)) {
            is DnsParseResult.Parsed -> result.question
            is DnsParseResult.Unsupported -> null
        }

    fun parse(
        packet: ByteArray,
        length: Int,
    ): DnsParseResult {
        if (length < MIN_IP_HEADER_SIZE) return unsupported("too-short", packet, length)
        return when (packet[0].highNibble()) {
            IPV4_VERSION -> parseIpv4(packet, length)
            IPV6_VERSION -> parseIpv6(packet, length)
            else -> unsupported("unsupported-ip-version", packet, length)
        }
    }

    private fun parseIpv4(
        packet: ByteArray,
        length: Int,
    ): DnsParseResult {
        if (length < MIN_IPV4_UDP_DNS_SIZE) return unsupported("ipv4-too-short", packet, length)
        val ipHeaderLength = (packet[0].toInt() and IPV4_HEADER_MASK) * INT_SIZE_BYTES
        if (ipHeaderLength < MIN_IPV4_HEADER_SIZE || length < ipHeaderLength + UDP_HEADER_SIZE + DNS_HEADER_SIZE) {
            return unsupported("ipv4-invalid-header", packet, length)
        }
        if ((packet[IPV4_PROTOCOL_OFFSET].toInt() and BYTE_MASK) != UDP_PROTOCOL) {
            return unsupported("not-udp", packet, length)
        }

        val udpOffset = ipHeaderLength
        val udpCheck = validateUdp(packet, length, udpOffset) ?: return unsupported("invalid-udp", packet, length)
        if (udpCheck.destinationPort != DNS_PORT) return unsupported("udp-not-dns", packet, length)
        return parseDnsPayload(
            packet = packet,
            length = length,
            ipVersion = IPV4_VERSION,
            dnsOffset = udpOffset + UDP_HEADER_SIZE,
            dnsLength = udpCheck.udpLength - UDP_HEADER_SIZE,
            sourceAddress = packet.copyOfRange(IPV4_SOURCE_OFFSET, IPV4_SOURCE_OFFSET + IPV4_ADDRESS_SIZE),
            destinationAddress = packet.copyOfRange(IPV4_DESTINATION_OFFSET, IPV4_DESTINATION_OFFSET + IPV4_ADDRESS_SIZE),
            sourcePort = udpCheck.sourcePort,
            destinationPort = udpCheck.destinationPort,
        )
    }

    private fun parseIpv6(
        packet: ByteArray,
        length: Int,
    ): DnsParseResult {
        if (length < MIN_IPV6_UDP_DNS_SIZE) return unsupported("ipv6-too-short", packet, length)
        if ((packet[IPV6_NEXT_HEADER_OFFSET].toInt() and BYTE_MASK) != UDP_PROTOCOL) {
            return unsupported("not-udp", packet, length)
        }
        val payloadLength = readUInt16(packet, IPV6_PAYLOAD_LENGTH_OFFSET)
        if (payloadLength < UDP_HEADER_SIZE + DNS_HEADER_SIZE || IPV6_HEADER_SIZE + payloadLength > length) {
            return unsupported("ipv6-invalid-payload", packet, length)
        }
        val udpOffset = IPV6_HEADER_SIZE
        val udpCheck = validateUdp(packet, length, udpOffset) ?: return unsupported("invalid-udp", packet, length)
        if (udpCheck.destinationPort != DNS_PORT) return unsupported("udp-not-dns", packet, length)
        return parseDnsPayload(
            packet = packet,
            length = length,
            ipVersion = IPV6_VERSION,
            dnsOffset = udpOffset + UDP_HEADER_SIZE,
            dnsLength = udpCheck.udpLength - UDP_HEADER_SIZE,
            sourceAddress = packet.copyOfRange(IPV6_SOURCE_OFFSET, IPV6_SOURCE_OFFSET + IPV6_ADDRESS_SIZE),
            destinationAddress = packet.copyOfRange(IPV6_DESTINATION_OFFSET, IPV6_DESTINATION_OFFSET + IPV6_ADDRESS_SIZE),
            sourcePort = udpCheck.sourcePort,
            destinationPort = udpCheck.destinationPort,
        )
    }

    private fun validateUdp(
        packet: ByteArray,
        length: Int,
        udpOffset: Int,
    ): UdpHeader? {
        if (length < udpOffset + UDP_HEADER_SIZE + DNS_HEADER_SIZE) return null
        val sourcePort = readUInt16(packet, udpOffset)
        val destinationPort = readUInt16(packet, udpOffset + PORT_FIELD_SIZE)
        val udpLength = readUInt16(packet, udpOffset + UDP_LENGTH_OFFSET)
        if (udpLength < UDP_HEADER_SIZE + DNS_HEADER_SIZE || udpOffset + udpLength > length) return null
        return UdpHeader(sourcePort, destinationPort, udpLength)
    }

    private fun parseDnsPayload(
        packet: ByteArray,
        length: Int,
        ipVersion: Int,
        dnsOffset: Int,
        dnsLength: Int,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
    ): DnsParseResult {
        if (dnsLength < DNS_HEADER_SIZE || readUInt16(packet, dnsOffset + DNS_QDCOUNT_OFFSET) < 1) {
            return unsupported("dns-no-question", packet, length)
        }

        val transactionId = readUInt16(packet, dnsOffset)
        val labels = mutableListOf<String>()
        var cursor = dnsOffset + DNS_HEADER_SIZE

        while (cursor < length) {
            val labelLength = packet[cursor].toInt() and BYTE_MASK
            if (labelLength == 0) {
                cursor += 1
                break
            }
            if (labelLength and DNS_POINTER_MASK == DNS_POINTER_MASK) return unsupported("dns-compressed-question", packet, length)
            if (labelLength > MAX_LABEL_LENGTH || cursor + 1 + labelLength > length) {
                return unsupported("dns-invalid-label", packet, length)
            }

            val label = packet.decodeAsciiLabel(cursor + 1, labelLength) ?: return unsupported("dns-non-ascii", packet, length)
            if (!label.isValidDomainLabel()) return unsupported("dns-invalid-domain", packet, length)
            labels += label.lowercase()
            cursor += 1 + labelLength
        }

        val dnsEnd = dnsOffset + dnsLength
        if (labels.isEmpty() || cursor + QUESTION_TRAILER_SIZE > dnsEnd) return unsupported("dns-invalid-question", packet, length)
        val domain = labels.joinToString(".")
        if (!domain.isValidDomain()) return unsupported("dns-invalid-domain", packet, length)

        val type = readUInt16(packet, cursor)
        val queryPayload = packet.copyOfRange(dnsOffset, dnsEnd)
        return DnsParseResult.Parsed(
            DnsQuestion(
                ipVersion = ipVersion,
                transactionId = transactionId,
                domain = domain,
                type = type,
                sourceAddress = sourceAddress,
                destinationAddress = destinationAddress,
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                queryPayload = queryPayload,
            ),
        )
    }

    fun inspect(
        packet: ByteArray,
        length: Int,
    ): VpnPacketDiagnostic =
        when (val result = parse(packet, length)) {
            is DnsParseResult.Parsed ->
                VpnPacketDiagnostic(
                    ipVersion = result.question.ipVersion,
                    protocol = "udp",
                    sourcePort = result.question.sourcePort,
                    destinationPort = result.question.destinationPort,
                    looksLikeDns = true,
                    reason = "parsed",
                )
            is DnsParseResult.Unsupported -> result.diagnostic
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

    private fun unsupported(
        reason: String,
        packet: ByteArray,
        length: Int,
    ): DnsParseResult.Unsupported = DnsParseResult.Unsupported(packet.diagnostic(reason, length))

    private fun ByteArray.diagnostic(
        reason: String,
        length: Int,
    ): VpnPacketDiagnostic {
        if (length < MIN_IP_HEADER_SIZE) {
            return VpnPacketDiagnostic(ipVersion = 0, protocol = "unknown", reason = reason)
        }
        val version = this[0].highNibble()
        val protocolNumber =
            when (version) {
                IPV4_VERSION -> if (length > IPV4_PROTOCOL_OFFSET) this[IPV4_PROTOCOL_OFFSET].toInt() and BYTE_MASK else -1
                IPV6_VERSION -> if (length > IPV6_NEXT_HEADER_OFFSET) this[IPV6_NEXT_HEADER_OFFSET].toInt() and BYTE_MASK else -1
                else -> -1
            }
        val transportOffset =
            when (version) {
                IPV4_VERSION -> {
                    val headerLength = (this[0].toInt() and IPV4_HEADER_MASK) * INT_SIZE_BYTES
                    headerLength.takeIf {
                        protocolNumber in PortBearingProtocols && length >= it + PORT_HEADER_MIN_SIZE
                    }
                }
                IPV6_VERSION ->
                    IPV6_HEADER_SIZE.takeIf {
                        protocolNumber in PortBearingProtocols && length >= IPV6_HEADER_SIZE + PORT_HEADER_MIN_SIZE
                    }
                else -> null
            }
        val srcPort = transportOffset?.let { readUInt16(this, it) }
        val dstPort = transportOffset?.let { readUInt16(this, it + PORT_FIELD_SIZE) }
        return VpnPacketDiagnostic(
            ipVersion = version,
            protocol = protocolNumber.toProtocolName(),
            sourcePort = srcPort,
            destinationPort = dstPort,
            looksLikeDns = srcPort == DNS_PORT || dstPort == DNS_PORT,
            reason = reason,
        )
    }

    private fun Int.toProtocolName(): String =
        when (this) {
            UDP_PROTOCOL -> "udp"
            TCP_PROTOCOL -> "tcp"
            ICMP_PROTOCOL -> "icmp"
            ICMPV6_PROTOCOL -> "icmpv6"
            else -> if (this >= 0) "protocol-$this" else "unknown"
        }

    private data class UdpHeader(
        val sourcePort: Int,
        val destinationPort: Int,
        val udpLength: Int,
    )

    private companion object {
        const val BYTE_MASK = 0xFF
        const val BYTE_SIZE_BITS = 8
        val ASCII_PRINTABLE_RANGE = 0x21..0x7E
        const val DNS_PORT = 53
        const val DNS_HEADER_SIZE = 12
        const val DNS_POINTER_MASK = 0xC0
        const val DNS_QDCOUNT_OFFSET = 4
        const val INT_SIZE_BYTES = 4
        const val ICMP_PROTOCOL = 1
        const val ICMPV6_PROTOCOL = 58
        const val IPV4_DESTINATION_OFFSET = 16
        const val IPV4_PROTOCOL_OFFSET = 9
        const val IPV4_SOURCE_OFFSET = 12
        const val IPV6_ADDRESS_SIZE = 16
        const val IPV6_DESTINATION_OFFSET = 24
        const val IPV6_HEADER_SIZE = 40
        const val IPV6_NEXT_HEADER_OFFSET = 6
        const val IPV6_PAYLOAD_LENGTH_OFFSET = 4
        const val IPV6_SOURCE_OFFSET = 8
        const val IPV4_ADDRESS_SIZE = 4
        const val IPV4_HEADER_MASK = 0x0F
        const val IPV4_VERSION = 4
        const val IPV6_VERSION = 6
        const val MAX_DOMAIN_LENGTH = 253
        const val MAX_LABEL_LENGTH = 63
        const val MIN_IP_HEADER_SIZE = 1
        const val MIN_IPV4_HEADER_SIZE = 20
        const val MIN_IPV4_UDP_DNS_SIZE = 40
        const val MIN_IPV6_UDP_DNS_SIZE = 60
        const val NIBBLE_SHIFT = 4
        const val PORT_FIELD_SIZE = 2
        const val PORT_HEADER_MIN_SIZE = 4
        const val QUESTION_TRAILER_SIZE = 4
        const val UDP_HEADER_SIZE = 8
        const val UDP_LENGTH_OFFSET = 4
        const val UDP_PROTOCOL = 17
        const val TCP_PROTOCOL = 6
        val PortBearingProtocols = setOf(UDP_PROTOCOL, TCP_PROTOCOL)
    }
}

sealed class DnsParseResult {
    data class Parsed(val question: DnsQuestion) : DnsParseResult()

    data class Unsupported(val diagnostic: VpnPacketDiagnostic) : DnsParseResult()
}

data class VpnPacketDiagnostic(
    val ipVersion: Int,
    val protocol: String,
    val sourcePort: Int? = null,
    val destinationPort: Int? = null,
    val looksLikeDns: Boolean = false,
    val reason: String,
)
