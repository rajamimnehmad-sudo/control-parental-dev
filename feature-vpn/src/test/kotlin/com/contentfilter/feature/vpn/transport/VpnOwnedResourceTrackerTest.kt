package com.contentfilter.feature.vpn.transport

import kotlin.test.Test
import kotlin.test.assertEquals

class VpnOwnedResourceTrackerTest {
    @Test
    fun `owned resources return exactly to zero after idempotent cleanup`() {
        val tracker = VpnOwnedResourceTracker()
        val bridge = tracker.acquire(VpnOwnedResourceKind.PacketBridgeFd, 3)
        val udp = tracker.acquire(VpnOwnedResourceKind.ProtectedUdp)

        assertEquals(4, tracker.snapshot().ownedFdResources)
        assertEquals(4, tracker.snapshot().ownedFdResourcesPeak)
        assertEquals(1, tracker.snapshot().activeProtectedUdpSocketsPeak)

        udp.close()
        udp.close()
        bridge.close()
        bridge.close()

        assertEquals(0, tracker.snapshot().ownedFdResources)
        assertEquals(0, tracker.snapshot().activeProtectedUdpSockets)
    }
}
