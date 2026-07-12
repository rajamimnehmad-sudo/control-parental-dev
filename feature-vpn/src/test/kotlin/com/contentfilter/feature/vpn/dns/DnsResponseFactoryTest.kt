package com.contentfilter.feature.vpn.dns

import com.contentfilter.core.domain.model.SearchEngineCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

class DnsResponseFactoryTest {
    @Test
    fun `SafeSearch response returns a CNAME for the original search host`() {
        val packet =
            DnsResponseFactory().cnamePacket(
                question = question(domain = "google.com"),
                canonicalName = "forcesafesearch.google.com",
            )
        val dnsOffset = Ipv4HeaderSize + UdpHeaderSize
        val questionNameSize = encodedNameSize("google.com")
        val answerOffset = dnsOffset + DnsHeaderSize + questionNameSize + QuestionTrailerSize
        val targetOffset = answerOffset + AnswerFixedSize

        assertEquals(0x1234, readUInt16(packet, dnsOffset))
        assertEquals(1, readUInt16(packet, dnsOffset + AnswerCountOffset))
        assertEquals(DnsTypeCname, readUInt16(packet, answerOffset + 2))
        assertEquals("forcesafesearch.google.com", decodeName(packet, targetOffset))
    }

    @Test
    fun `Google Bing and DuckDuckGo use their strict DNS targets`() {
        mapOf(
            "google.com" to "forcesafesearch.google.com",
            "bing.com" to "strict.bing.com",
            "duckduckgo.com" to "safe.duckduckgo.com",
        ).forEach { (domain, expectedTarget) ->
            val target = SearchEngineCatalog.safeSearchDnsTarget(domain)
            val packet = DnsResponseFactory().cnamePacket(question(domain), requireNotNull(target))
            val targetOffset =
                Ipv4HeaderSize + UdpHeaderSize + DnsHeaderSize +
                    encodedNameSize(domain) + QuestionTrailerSize + AnswerFixedSize

            assertEquals(expectedTarget, decodeName(packet, targetOffset))
        }
    }

    @Test
    fun `SafeSearch rewrite also answers Chromium HTTPS DNS records`() {
        val packet =
            DnsResponseFactory().cnamePacket(
                question = question(domain = "google.com", type = DnsTypeHttps),
                canonicalName = "forcesafesearch.google.com",
            )
        val dnsOffset = Ipv4HeaderSize + UdpHeaderSize
        val typeOffset = dnsOffset + DnsHeaderSize + encodedNameSize("google.com")

        assertEquals(DnsTypeHttps, readUInt16(packet, typeOffset))
    }

    private fun question(
        domain: String,
        type: Int = DnsTypeA,
    ): DnsQuestion =
        DnsQuestion(
            transactionId = 0x1234,
            domain = domain,
            type = type,
            sourceAddress = byteArrayOf(10, 0, 0, 2),
            destinationAddress = byteArrayOf(8, 8, 8, 8),
            sourcePort = 53_000,
            destinationPort = 53,
            queryPayload = byteArrayOf(),
        )

    private fun encodedNameSize(domain: String): Int = domain.split('.').sumOf { it.length + 1 } + 1

    private fun decodeName(
        data: ByteArray,
        start: Int,
    ): String {
        val labels = mutableListOf<String>()
        var offset = start
        while (data[offset].toInt() != 0) {
            val length = data[offset].toInt() and 0xFF
            labels += data.copyOfRange(offset + 1, offset + 1 + length).decodeToString()
            offset += length + 1
        }
        return labels.joinToString(".")
    }

    private fun readUInt16(
        data: ByteArray,
        offset: Int,
    ): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private companion object {
        const val AnswerCountOffset = 6
        const val AnswerFixedSize = 12
        const val DnsHeaderSize = 12
        const val DnsTypeA = 1
        const val DnsTypeCname = 5
        const val DnsTypeHttps = 65
        const val Ipv4HeaderSize = 20
        const val QuestionTrailerSize = 4
        const val UdpHeaderSize = 8
    }
}
