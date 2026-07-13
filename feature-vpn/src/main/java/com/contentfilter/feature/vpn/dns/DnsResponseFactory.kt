package com.contentfilter.feature.vpn.dns

/**
 * Builds DNS payloads for simple local responses.
 */
class DnsResponseFactory {
    fun addressPacket(
        question: DnsQuestion,
        addresses: List<ByteArray>,
    ): ByteArray = responsePacket(question, addressPayload(question, addresses))

    fun noDataPacket(question: DnsQuestion): ByteArray = responsePacket(question, noDataPayload(question))

    fun cnamePacket(
        question: DnsQuestion,
        canonicalName: String,
    ): ByteArray = responsePacket(question, cnamePayload(question, canonicalName))

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
    ): ByteArray =
        if (question.ipVersion == IPV6_VERSION) {
            ipv6ResponsePacket(question, payload)
        } else {
            ipv4ResponsePacket(question, payload)
        }

    private fun ipv4ResponsePacket(
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

    private fun ipv6ResponsePacket(
        question: DnsQuestion,
        payload: ByteArray,
    ): ByteArray {
        val packet = ByteArray(IPV6_HEADER_SIZE + UDP_HEADER_SIZE + payload.size)
        packet[0] = IPV6_VERSION_AND_TRAFFIC_CLASS.toByte()
        writeUInt16(packet, IPV6_PAYLOAD_LENGTH_OFFSET, UDP_HEADER_SIZE + payload.size)
        packet[IPV6_NEXT_HEADER_OFFSET] = UDP_PROTOCOL.toByte()
        packet[IPV6_HOP_LIMIT_OFFSET] = DEFAULT_TTL.toByte()
        question.destinationAddress.copyInto(packet, IPV6_SOURCE_OFFSET)
        question.sourceAddress.copyInto(packet, IPV6_DESTINATION_OFFSET)

        val udpOffset = IPV6_HEADER_SIZE
        writeUInt16(packet, udpOffset, question.destinationPort)
        writeUInt16(packet, udpOffset + PORT_FIELD_SIZE, question.sourcePort)
        writeUInt16(packet, udpOffset + UDP_LENGTH_OFFSET, UDP_HEADER_SIZE + payload.size)
        payload.copyInto(packet, IPV6_HEADER_SIZE + UDP_HEADER_SIZE)
        writeUInt16(
            packet,
            udpOffset + UDP_CHECKSUM_OFFSET,
            udp6Checksum(packet, udpOffset, UDP_HEADER_SIZE + payload.size),
        )
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

    private fun cnamePayload(
        question: DnsQuestion,
        canonicalName: String,
    ): ByteArray {
        val originalQuestion = question.originalQuestionBytes()
        val targetName = encodeDomain(canonicalName)
        val payload =
            ByteArray(
                DNS_HEADER_SIZE +
                    originalQuestion.size +
                    ANSWER_FIXED_SIZE + targetName.size,
            )
        writeUInt16(payload, TRANSACTION_ID_OFFSET, question.transactionId)
        payload[FLAGS_OFFSET] = RESPONSE_FLAGS_HIGH.toByte()
        payload[FLAGS_OFFSET + 1] = NOERROR_FLAGS_LOW.toByte()
        writeUInt16(payload, QUESTION_COUNT_OFFSET, 1)
        writeUInt16(payload, ANSWER_COUNT_OFFSET, 1)
        writeUInt16(payload, AUTHORITY_COUNT_OFFSET, 0)
        writeUInt16(payload, ADDITIONAL_COUNT_OFFSET, 0)
        var offset = DNS_HEADER_SIZE
        originalQuestion.copyInto(payload, offset)
        offset += originalQuestion.size
        writeUInt16(payload, offset, QUESTION_NAME_POINTER)
        writeUInt16(payload, offset + SHORT_SIZE_BYTES, DNS_TYPE_CNAME)
        writeUInt16(payload, offset + SHORT_SIZE_BYTES * 2, DNS_CLASS_IN)
        writeUInt32(payload, offset + SHORT_SIZE_BYTES * 3, CNAME_TTL_SECONDS)
        writeUInt16(payload, offset + SHORT_SIZE_BYTES * 5, targetName.size)
        targetName.copyInto(payload, offset + ANSWER_FIXED_SIZE)
        return payload
    }

    private fun addressPayload(
        question: DnsQuestion,
        addresses: List<ByteArray>,
    ): ByteArray {
        val expectedAddressSize =
            when (question.type) {
                DNS_TYPE_A -> IPV4_ADDRESS_SIZE
                DNS_TYPE_AAAA -> IPV6_ADDRESS_SIZE
                else -> error("Address response requires an A or AAAA question.")
            }
        require(addresses.isNotEmpty() && addresses.all { it.size == expectedAddressSize })
        val originalQuestion = question.originalQuestionBytes()
        val payload =
            ByteArray(
                DNS_HEADER_SIZE + originalQuestion.size +
                    addresses.sumOf { ANSWER_FIXED_SIZE + it.size },
            )
        prepareResponseHeader(payload, question, addresses.size)
        var offset = DNS_HEADER_SIZE
        originalQuestion.copyInto(payload, offset)
        offset += originalQuestion.size
        addresses.forEach { address ->
            writeUInt16(payload, offset, QUESTION_NAME_POINTER)
            writeUInt16(payload, offset + SHORT_SIZE_BYTES, question.type)
            writeUInt16(payload, offset + SHORT_SIZE_BYTES * 2, DNS_CLASS_IN)
            writeUInt32(payload, offset + SHORT_SIZE_BYTES * 3, ADDRESS_TTL_SECONDS)
            writeUInt16(payload, offset + SHORT_SIZE_BYTES * 5, address.size)
            address.copyInto(payload, offset + ANSWER_FIXED_SIZE)
            offset += ANSWER_FIXED_SIZE + address.size
        }
        return payload
    }

    private fun noDataPayload(question: DnsQuestion): ByteArray {
        val originalQuestion = question.originalQuestionBytes()
        return ByteArray(DNS_HEADER_SIZE + originalQuestion.size).also { payload ->
            prepareResponseHeader(payload, question, answerCount = 0)
            originalQuestion.copyInto(payload, DNS_HEADER_SIZE)
        }
    }

    private fun prepareResponseHeader(
        payload: ByteArray,
        question: DnsQuestion,
        answerCount: Int,
    ) {
        writeUInt16(payload, TRANSACTION_ID_OFFSET, question.transactionId)
        payload[FLAGS_OFFSET] = RESPONSE_FLAGS_HIGH.toByte()
        payload[FLAGS_OFFSET + 1] = NOERROR_FLAGS_LOW.toByte()
        writeUInt16(payload, QUESTION_COUNT_OFFSET, 1)
        writeUInt16(payload, ANSWER_COUNT_OFFSET, answerCount)
        writeUInt16(payload, AUTHORITY_COUNT_OFFSET, 0)
        writeUInt16(payload, ADDITIONAL_COUNT_OFFSET, 0)
    }

    private fun DnsQuestion.originalQuestionBytes(): ByteArray {
        val payload = queryPayload
        if (payload.size >= DNS_HEADER_SIZE + QUESTION_TRAILER_SIZE) {
            var cursor = DNS_HEADER_SIZE
            while (cursor < payload.size) {
                val labelLength = payload[cursor].toInt() and BYTE_MASK
                cursor += 1
                if (labelLength == 0) {
                    val questionEnd = cursor + QUESTION_TRAILER_SIZE
                    if (questionEnd <= payload.size) {
                        return payload.copyOfRange(DNS_HEADER_SIZE, questionEnd)
                    }
                    break
                }
                if (labelLength > MAX_DNS_LABEL_LENGTH || cursor + labelLength > payload.size) break
                cursor += labelLength
            }
        }
        return encodeDomain(domain) +
            byteArrayOf(
                (type ushr BYTE_SIZE_BITS).toByte(),
                type.toByte(),
                0,
                DNS_CLASS_IN.toByte(),
            )
    }

    private fun encodeDomain(domain: String): ByteArray {
        val labels = domain.trim().lowercase().removeSuffix(".").split('.')
        val output = ByteArray(labels.sumOf { it.length + 1 } + 1)
        var offset = 0
        labels.forEach { label ->
            require(label.isNotBlank() && label.length <= MAX_DNS_LABEL_LENGTH)
            output[offset++] = label.length.toByte()
            label.forEach { character -> output[offset++] = character.code.toByte() }
        }
        output[offset] = 0
        return output
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

    private fun writeUInt32(
        data: ByteArray,
        offset: Int,
        value: Int,
    ) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
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

    private fun udp6Checksum(
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = 0
        sum = addBytes(sum, packet, IPV6_SOURCE_OFFSET, IPV6_ADDRESS_SIZE)
        sum = addBytes(sum, packet, IPV6_DESTINATION_OFFSET, IPV6_ADDRESS_SIZE)
        sum = addUInt32(sum, udpLength)
        sum += UDP_PROTOCOL
        sum = addBytes(sum, packet, udpOffset, udpLength)
        while (sum ushr SHORT_SIZE_BITS != 0) {
            sum = (sum and SHORT_MASK) + (sum ushr SHORT_SIZE_BITS)
        }
        val checksum = sum.inv() and SHORT_MASK
        return checksum.takeUnless { it == 0 } ?: SHORT_MASK
    }

    private fun addUInt32(
        initial: Int,
        value: Int,
    ): Int = initial + ((value ushr SHORT_SIZE_BITS) and SHORT_MASK) + (value and SHORT_MASK)

    private fun addBytes(
        initial: Int,
        data: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        var sum = initial
        var index = 0
        while (index < length) {
            val high = data[offset + index].toInt() and BYTE_MASK
            val low = if (index + 1 < length) data[offset + index + 1].toInt() and BYTE_MASK else 0
            sum += (high shl BYTE_SIZE_BITS) or low
            index += SHORT_SIZE_BYTES
        }
        return sum
    }

    private companion object {
        const val ADDITIONAL_COUNT_OFFSET = 10
        const val ANSWER_FIXED_SIZE = 12
        const val ANSWER_COUNT_OFFSET = 6
        const val AUTHORITY_COUNT_OFFSET = 8
        const val BYTE_MASK = 0xFF
        const val BYTE_SIZE_BITS = 8
        const val DEFAULT_TTL = 64
        const val CNAME_TTL_SECONDS = 60
        const val ADDRESS_TTL_SECONDS = 60
        const val DNS_CLASS_IN = 1
        const val DNS_TYPE_A = 1
        const val DNS_TYPE_AAAA = 28
        const val DNS_HEADER_SIZE = 12
        const val DNS_TYPE_CNAME = 5
        const val FLAGS_OFFSET = 2
        const val IP_DESTINATION_OFFSET = 16
        const val IP_SOURCE_OFFSET = 12
        const val IPV6_ADDRESS_SIZE = 16
        const val IPV4_ADDRESS_SIZE = 4
        const val IPV6_DESTINATION_OFFSET = 24
        const val IPV6_HEADER_SIZE = 40
        const val IPV6_HOP_LIMIT_OFFSET = 7
        const val IPV6_NEXT_HEADER_OFFSET = 6
        const val IPV6_PAYLOAD_LENGTH_OFFSET = 4
        const val IPV6_SOURCE_OFFSET = 8
        const val IPV6_VERSION = 6
        const val IPV6_VERSION_AND_TRAFFIC_CLASS = 0x60
        const val IPV4_CHECKSUM_OFFSET = 10
        const val IPV4_HEADER_SIZE = 20
        const val IPV4_VERSION_AND_HEADER_LENGTH = 0x45
        const val NXDOMAIN_FLAGS_LOW = 0x83
        const val NOERROR_FLAGS_LOW = 0x80
        const val SERVFAIL_FLAGS_LOW = 0x82
        const val PORT_FIELD_SIZE = 2
        const val QUESTION_COUNT_OFFSET = 4
        const val QUESTION_NAME_POINTER = 0xC00C
        const val QUESTION_TRAILER_SIZE = 4
        const val RESPONSE_FLAGS_HIGH = 0x81
        const val SHORT_MASK = 0xFFFF
        const val SHORT_SIZE_BITS = 16
        const val SHORT_SIZE_BYTES = 2
        const val TOTAL_LENGTH_OFFSET = 2
        const val TRANSACTION_ID_OFFSET = 0
        const val UDP_HEADER_SIZE = 8
        const val UDP_CHECKSUM_OFFSET = 6
        const val UDP_LENGTH_OFFSET = 4
        const val UDP_PROTOCOL = 17
        const val MAX_DNS_LABEL_LENGTH = 63
    }
}
