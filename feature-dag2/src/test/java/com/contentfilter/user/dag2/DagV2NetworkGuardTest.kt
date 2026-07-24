package com.contentfilter.user.dag2

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagV2NetworkGuardTest {
    @Test
    fun `private loopback link local cgnat and documentation ranges are rejected`() {
        val blocked = listOf("127.0.0.1", "10.1.2.3", "169.254.1.2", "100.64.1.2", "192.0.2.1", "fc00::1")

        blocked.forEach { literal ->
            val address = InetAddress.getByName(literal)
            assertFalse(with(DagV2NetworkGuard.Companion) { address.isPublicDagV2Address() }, literal)
        }
    }

    @Test
    fun `known public addresses pass the local range guard`() {
        val address = InetAddress.getByName("8.8.8.8")

        assertTrue(with(DagV2NetworkGuard.Companion) { address.isPublicDagV2Address() })
    }
}
