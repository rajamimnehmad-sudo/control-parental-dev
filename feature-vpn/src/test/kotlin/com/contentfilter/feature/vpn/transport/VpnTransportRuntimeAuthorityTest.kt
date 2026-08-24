package com.contentfilter.feature.vpn.transport

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VpnTransportRuntimeAuthorityTest {
    @AfterTest
    fun reset() {
        VpnTransportRuntimeAuthority.resetForTest()
    }

    @Test
    fun `clean shutdown permits a new generation`() {
        val first = VpnTransportRuntimeAuthority.begin()
        VpnTransportRuntimeAuthority.finish(first, clean = true, dirtyReason = null)

        val second = VpnTransportRuntimeAuthority.begin()

        assertEquals(first + 1, second)
        assertEquals(VpnTransportRuntimeState.Running, VpnTransportRuntimeAuthority.snapshot().state)
    }

    @Test
    fun `dirty SOCKS or HEV shutdown quarantines and rejects restart`() {
        val token = VpnTransportRuntimeAuthority.begin()
        VpnTransportRuntimeAuthority.finish(token, clean = false, dirtyReason = "socks_dirty_shutdown")

        assertEquals(VpnTransportRuntimeState.Quarantined, VpnTransportRuntimeAuthority.snapshot().state)
        assertFailsWith<IllegalStateException> { VpnTransportRuntimeAuthority.begin() }
    }

    @Test
    fun `late proven cleanup releases quarantine exactly for current generation`() {
        val token = VpnTransportRuntimeAuthority.begin()
        VpnTransportRuntimeAuthority.finish(token, clean = false, dirtyReason = "hev_quarantined")
        VpnTransportRuntimeAuthority.finish(token, clean = true, dirtyReason = null)

        assertEquals(VpnTransportRuntimeState.Ready, VpnTransportRuntimeAuthority.snapshot().state)
    }
}
