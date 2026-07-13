package com.contentfilter.feature.vpn.dns

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DnsAnswerAddressParserTest {
    @Test
    fun `extracts IPv4 and IPv6 answers with shortest ttl`() {
        val parsed =
            DnsAnswerAddressParser.parse(
                response(
                    record(type = AddressTypeIpv4, ttlSeconds = 300, data = byteArrayOf(192.toByte(), 0, 2, 10)),
                    record(type = AddressTypeIpv6, ttlSeconds = 120, data = Ipv6Address),
                ),
            )

        assertEquals(
            setOf("192.0.2.10", "2001:db8:0:0:0:0:0:10"),
            parsed?.addresses?.map { it.hostAddress.orEmpty() }?.toSet(),
        )
        assertEquals(120_000L, parsed?.ttlMillis)
    }

    @Test
    fun `ignores unrelated records and malformed responses`() {
        assertNull(DnsAnswerAddressParser.parse(response(record(type = 5, ttlSeconds = 60, data = byteArrayOf(0)))))
        assertNull(DnsAnswerAddressParser.parse(byteArrayOf(0, 1, 2)))
    }

    private fun response(vararg records: ByteArray): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeShort(1)
                output.writeShort(0x8180)
                output.writeShort(1)
                output.writeShort(records.size)
                output.writeShort(0)
                output.writeShort(0)
                output.write(Name)
                output.writeShort(AddressTypeIpv4)
                output.writeShort(1)
                records.forEach(output::write)
            }
            bytes.toByteArray()
        }

    private fun record(
        type: Int,
        ttlSeconds: Int,
        data: ByteArray,
    ): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeShort(0xC00C)
                output.writeShort(type)
                output.writeShort(1)
                output.writeInt(ttlSeconds)
                output.writeShort(data.size)
                output.write(data)
            }
            bytes.toByteArray()
        }

    private companion object {
        const val AddressTypeIpv4 = 1
        const val AddressTypeIpv6 = 28
        val Name =
            byteArrayOf(7) + "example".encodeToByteArray() +
                byteArrayOf(3) + "com".encodeToByteArray() + byteArrayOf(0)
        val Ipv6Address = byteArrayOf(0x20, 0x01, 0x0D, 0xB8.toByte()) + ByteArray(11) + byteArrayOf(0x10)
    }
}
