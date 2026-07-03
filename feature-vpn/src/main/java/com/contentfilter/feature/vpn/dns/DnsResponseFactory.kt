package com.contentfilter.feature.vpn.dns

/**
 * Builds DNS payloads for simple local responses.
 */
class DnsResponseFactory {
    fun nxdomainPacket(question: DnsQuestion): ByteArray {
        val payload = nxdomainPayload(question)
        return responsePacket(question, payload)
    }

    fun servfailPacket(question: DnsQuestion): ByteArray {
        val payload = failurePayload(question, SERVFAIL_FLAGS_LOW)
        return responsePacket(question, payload)
    }

    fun responsePacket(
        question: DnsQuestion,
        payload: ByteArray,
    ): ByteArray {
        val packet = ByteArray(IPV4_HEADER_SIZE + UDP_HEADER_SIZE + payload.size)
        packet[0] = IPV4_VERSION_AND_HEADER_LENGTH.toByte()
        packet[8] = DEFAULT_TTL.toByte()
        packet[9] = UDP_PROTOCOL.toByte()
        writeUInt16(packet, TOTAL_LENGTH_OFFSET, packet.size)
        question.destinationAddress.copyInto(packet, IP_SOURCE_OFFSET)
        question.sourceAddress.copyInto(packet, IP_DESTINATION_OFFSET)
        writeUInt16(packet, IPV4_CHECKSUM_OFFSET, ipv4Checksum(packet))

        val udpOffset = IPV4_HEADER_SIZE
        writeUInt16(packet, udpOffset, question.destinationPort)
        writeUInt16(packet, udpOffset + PORT_FIELD_SIZE, question.sourcePort)
        writeUInt16(packet, udpOffset + UDP_LENGTH_OFFSET, UDP_HEADER_SIZE + payload.size)
        payload.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)
        return packet
    }

    private fun nxdomainPayload(question: DnsQuestion): ByteArray {
        val response = question.queryPayload.copyOf()
        if (response.size < DNS_HEADER_SIZE) return response
        response[FLAGS_OFFSET] = RESPONSE_FLAGS_HIGH.toByte()
        response[FLAGS_OFFSET + 1] = NXDOMAIN_FLAGS_LOW.toByte()
        clearCounts(response)
        return response
    }

    private fun failurePayload(
        question: DnsQuestion,
        lowFlags: Int,
    ): ByteArray {
        val response = question.queryPayload.copyOf()
        if (response.size < DNS_HEADER_SIZE) return response
        response[FLAGS_OFFSET] = RESPONSE_FLAGS_HIGH.toByte()
        response[FLAGS_OFFSET + 1] = lowFlags.toByte()
        clearCounts(response)
        return response
    }

    private fun clearCounts(response: ByteArray) {
        response[ANSWER_COUNT_OFFSET] = 0
        response[ANSWER_COUNT_OFFSET + 1] = 0
        response[AUTHORITY_COUNT_OFFSET] = 0
        response[AUTHORITY_COUNT_OFFSET + 1] = 0
        response[ADDITIONAL_COUNT_OFFSET] = 0
        response[ADDITIONAL_COUNT_OFFSET + 1] = 0
    }

    private fun writeUInt16(
        data: ByteArray,
        offset: Int,
        value: Int,
    ) {
        data[offset] = (value ushr BYTE_SIZE_BITS).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun ipv4Checksum(packet: ByteArray): Int {
        var sum = 0
        var index = 0
        while (index < IPV4_HEADER_SIZE) {
            sum += ((packet[index].toInt() and BYTE_MASK) shl BYTE_SIZE_BITS) or
                (packet[index + 1].toInt() and BYTE_MASK)
            index += SHORT_SIZE_BYTES
        }
        while (sum ushr SHORT_SIZE_BITS != 0) {
            sum = (sum and SHORT_MASK) + (sum ushr SHORT_SIZE_BITS)
        }
        return sum.inv() and SHORT_MASK
    }

    private companion object {
        const val ADDITIONAL_COUNT_OFFSET = 10
        const val ANSWER_COUNT_OFFSET = 6
        const val AUTHORITY_COUNT_OFFSET = 8
        const val BYTE_MASK = 0xFF
        const val BYTE_SIZE_BITS = 8
        const val DEFAULT_TTL = 64
        const val DNS_HEADER_SIZE = 12
        const val FLAGS_OFFSET = 2
        const val IP_DESTINATION_OFFSET = 16
        const val IP_SOURCE_OFFSET = 12
        const val IPV4_CHECKSUM_OFFSET = 10
        const val IPV4_HEADER_SIZE = 20
        const val IPV4_VERSION_AND_HEADER_LENGTH = 0x45
        const val NXDOMAIN_FLAGS_LOW = 0x83
        const val SERVFAIL_FLAGS_LOW = 0x82
        const val PORT_FIELD_SIZE = 2
        const val RESPONSE_FLAGS_HIGH = 0x81
        const val SHORT_MASK = 0xFFFF
        const val SHORT_SIZE_BITS = 16
        const val SHORT_SIZE_BYTES = 2
        const val TOTAL_LENGTH_OFFSET = 2
        const val UDP_HEADER_SIZE = 8
        const val UDP_LENGTH_OFFSET = 4
        const val UDP_PROTOCOL = 17
    }
}
