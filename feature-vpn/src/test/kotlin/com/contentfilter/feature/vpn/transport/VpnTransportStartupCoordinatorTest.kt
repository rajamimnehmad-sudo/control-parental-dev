package com.contentfilter.feature.vpn.transport

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VpnTransportStartupCoordinatorTest {
    @AfterTest
    fun reset() {
        VpnTransportRuntimeAuthority.resetForTest()
    }

    @Test
    fun `quarantined runtime rejects before any transport side effect`() {
        val dirtyToken = VpnTransportRuntimeAuthority.begin()
        VpnTransportRuntimeAuthority.finish(dirtyToken, clean = false, dirtyReason = "hev_quarantined")
        var socksStarts = 0
        var engineStarts = 0
        var resourcesOpened = 0
        var cleanupCalls = 0

        assertFailsWith<IllegalStateException> {
            VpnTransportStartupCoordinator.start(
                startTransport = {
                    socksStarts++
                    engineStarts++
                    resourcesOpened++
                },
                cleanupAfterFailure = {
                    cleanupCalls++
                    VpnTransportStartupCleanup(clean = true, dirtyReason = null)
                },
            )
        }

        assertEquals(0, socksStarts)
        assertEquals(0, engineStarts)
        assertEquals(0, resourcesOpened)
        assertEquals(0, cleanupCalls)
        assertEquals(VpnTransportRuntimeState.Quarantined, VpnTransportRuntimeAuthority.snapshot().state)
    }

    @Test
    fun `clean startup failure releases authority and permits next generation`() {
        var resourcesOpened = 0
        var cleanupCalls = 0

        assertFailsWith<IllegalStateException> {
            VpnTransportStartupCoordinator.start(
                startTransport = {
                    resourcesOpened++
                    error("startup_failed")
                },
                cleanupAfterFailure = {
                    cleanupCalls++
                    resourcesOpened = 0
                    VpnTransportStartupCleanup(clean = true, dirtyReason = null)
                },
            )
        }

        assertEquals(1, cleanupCalls)
        assertEquals(0, resourcesOpened)
        assertEquals(VpnTransportRuntimeState.Ready, VpnTransportRuntimeAuthority.snapshot().state)
        val nextToken =
            VpnTransportStartupCoordinator.start(
                startTransport = { it },
                cleanupAfterFailure = { error("cleanup must not run") },
            )
        assertEquals(VpnTransportRuntimeState.Running, VpnTransportRuntimeAuthority.snapshot().state)
        VpnTransportRuntimeAuthority.finish(nextToken, clean = true, dirtyReason = null)
    }

    @Test
    fun `dirty startup failure quarantines and prevents a second native start`() {
        var socksStarts = 0
        var engineStarts = 0

        assertFailsWith<IllegalStateException> {
            VpnTransportStartupCoordinator.start(
                startTransport = {
                    socksStarts++
                    engineStarts++
                    error("startup_failed")
                },
                cleanupAfterFailure = {
                    VpnTransportStartupCleanup(clean = false, dirtyReason = "resources_not_released")
                },
            )
        }
        assertFailsWith<IllegalStateException> {
            VpnTransportStartupCoordinator.start(
                startTransport = {
                    socksStarts++
                    engineStarts++
                },
                cleanupAfterFailure = { VpnTransportStartupCleanup(clean = true, dirtyReason = null) },
            )
        }

        assertEquals(1, socksStarts)
        assertEquals(1, engineStarts)
        assertEquals(VpnTransportRuntimeState.Quarantined, VpnTransportRuntimeAuthority.snapshot().state)
    }
}
