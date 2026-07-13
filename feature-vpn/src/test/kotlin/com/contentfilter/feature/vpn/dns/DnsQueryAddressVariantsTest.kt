package com.contentfilter.feature.vpn.dns

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DnsQueryAddressVariantsTest {
    @Test
    fun `replaces the question type with A and AAAA`() {
        val query =
            byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
                byteArrayOf(7) + "example".encodeToByteArray() +
                byteArrayOf(3) + "com".encodeToByteArray() + byteArrayOf(0, 0, 65, 0, 1)

        val variants = DnsQueryAddressVariants.from(query)

        assertEquals(2, variants.size)
        assertContentEquals(byteArrayOf(0, 1), variants[0].copyOfRange(25, 27))
        assertContentEquals(byteArrayOf(0, 28), variants[1].copyOfRange(25, 27))
    }

    @Test
    fun `rejects malformed questions`() {
        assertTrue(DnsQueryAddressVariants.from(byteArrayOf(0, 1, 2)).isEmpty())
    }
}
