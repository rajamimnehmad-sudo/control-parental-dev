package com.contentfilter.feature.vpn.dns

/**
 * DNS question extracted from a traditional UDP DNS packet.
 */
data class DnsQuestion(
    val transactionId: Int,
    val domain: String,
    val type: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val queryPayload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is DnsQuestion &&
            transactionId == other.transactionId &&
            domain == other.domain &&
            type == other.type &&
            sourceAddress.contentEquals(other.sourceAddress) &&
            destinationAddress.contentEquals(other.destinationAddress) &&
            sourcePort == other.sourcePort &&
            destinationPort == other.destinationPort &&
            queryPayload.contentEquals(other.queryPayload)

    override fun hashCode(): Int {
        var result = transactionId
        result = 31 * result + domain.hashCode()
        result = 31 * result + type
        result = 31 * result + sourceAddress.contentHashCode()
        result = 31 * result + destinationAddress.contentHashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + queryPayload.contentHashCode()
        return result
    }
}
