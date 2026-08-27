package com.contentfilter.feature.vpn.service

/** Compatibility facade for the 08B diagnostics; 09A uses [VpnPacketParser]. */
internal object VpnFlowTupleParser {
    fun parse(
        packet: ByteArray,
        length: Int,
    ): VpnFlowTuple? {
        val parsed = VpnPacketParser.parse(packet, length)
        return (parsed as? VpnPacketParseResult.Parsed)?.flow
    }
}
