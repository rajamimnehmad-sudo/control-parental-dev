package com.contentfilter.feature.vpn.service

import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnControllerSocketProtectionTest {
    private val owner = Any()

    @AfterTest
    fun clear() {
        VpnController.unregisterSocketProtector(owner)
    }

    @Test
    fun `missing active VpnService fails closed`() {
        assertFalse(VpnController.protectDevUpstreamSocket(Socket()))
    }

    @Test
    fun `active VpnService result is authoritative and stale unregister cannot clear replacement`() {
        val replacement = Any()
        VpnController.registerSocketProtector(owner) { true }
        assertTrue(VpnController.protectDevUpstreamSocket(Socket()))

        VpnController.registerSocketProtector(replacement) { false }
        VpnController.unregisterSocketProtector(owner)
        assertFalse(VpnController.protectDevUpstreamSocket(Socket()))
        VpnController.unregisterSocketProtector(replacement)
    }
}
