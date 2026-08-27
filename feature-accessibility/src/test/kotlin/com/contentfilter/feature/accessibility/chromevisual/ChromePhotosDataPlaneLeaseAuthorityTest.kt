package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosDataPlaneLeaseAuthorityTest {
    private var now = 10_000L
    private val authority = ChromePhotosDataPlaneLeaseAuthority { now }

    @Test
    fun `without a complete attestation no lease exists and surface remains opaque`() {
        val presentation = ChromePhotosDataPlanePresentationPolicy()
        presentation.cover(epoch = 7L)
        presentation.markOpaqueCommitted(epoch = 7L)
        val missingLease = authority.mint(attestation(proxyHealthy = false), context())

        assertNull(missingLease)
        assertFalse(presentation.canPresent(missingLease))
        assertFalse(presentation.isTransparent)
        assertNull(authority.mint(attestation(policyConfirmed = false), context()))
        assertNull(authority.mint(attestation(fixtureConfirmed = false), context()))
    }

    @Test
    fun `non DEV or stale heartbeat can never mint a lease`() {
        assertNull(authority.mint(attestation(devBuild = false), context()))
        now += ChromePhotosDataPlaneLeaseAuthority.MaximumHeartbeatAgeMillis + 1L
        assertNull(
            authority.mint(
                attestation(heartbeatElapsed = 10_000L, validUntilElapsed = now + 1_000L),
                context(),
            ),
        )
    }

    @Test
    fun `valid scoped attestation mints a short lived lease`() {
        val lease = assertNotNull(authority.mint(attestation(), context()))
        val presentation = ChromePhotosDataPlanePresentationPolicy()
        presentation.cover(epoch = lease.epoch)
        assertFalse(presentation.canPresent(lease))
        assertTrue(presentation.markOpaqueCommitted(lease.epoch))
        assertTrue(presentation.markTransparent(lease))

        assertTrue(authority.isValid(lease, attestation(), context()))
        assertTrue(presentation.isTransparent)
        assertTrue(lease.validUntilElapsed <= now + ChromePhotosDataPlaneLeaseAuthority.LeaseDurationMillis)
    }

    @Test
    fun `verified real web scope can mint without fixture script heartbeat`() {
        val lease =
            authority.mint(
                attestation(fixtureConfirmed = false, realWebScopeConfirmed = true),
                context(),
            )

        assertNotNull(lease)
    }

    @Test
    fun `healthy renewal replaces capability without reusing the old lease`() {
        val first = assertNotNull(authority.mint(attestation(), context()))
        now += 350L
        val renewed = assertNotNull(authority.mint(attestation(), context()))

        assertNotEquals(first.capabilityId, renewed.capabilityId)
        assertFalse(authority.isValid(first, attestation(), context()))
        assertTrue(authority.isValid(renewed, attestation(), context()))
    }

    @Test
    fun `expired or explicitly revoked lease becomes invalid immediately`() {
        val lease = assertNotNull(authority.mint(attestation(), context()))
        val presentation = ChromePhotosDataPlanePresentationPolicy()
        presentation.cover(lease.epoch)
        presentation.markOpaqueCommitted(lease.epoch)
        presentation.markTransparent(lease)
        now = lease.validUntilElapsed
        assertFalse(authority.isValid(lease, attestation(), context()))
        presentation.revoke()
        assertFalse(presentation.isTransparent)

        now = 20_000L
        val renewed = assertNotNull(authority.mint(attestation(), context()))
        authority.revoke()
        assertFalse(authority.isValid(renewed, attestation(), context()))
    }

    @Test
    fun `proxy or VPN loss invalidates the lease`() {
        val lease = assertNotNull(authority.mint(attestation(), context()))

        assertFalse(authority.isValid(lease, attestation(proxyHealthy = false), context()))
        assertFalse(authority.isValid(lease, attestation(vpnConfirmed = false), context()))
        assertFalse(authority.isValid(lease, attestation(vpnSessionId = "old-session"), context()))
        assertFalse(authority.isValid(lease, attestation(accessibilityBound = false), context()))
    }

    @Test
    fun `old epoch window viewport or non Chrome context cannot keep transparency`() {
        val lease = assertNotNull(authority.mint(attestation(), context()))
        val presentation = ChromePhotosDataPlanePresentationPolicy()
        presentation.cover(lease.epoch)
        presentation.markOpaqueCommitted(lease.epoch)
        presentation.markTransparent(lease)
        presentation.cover(epoch = 8L)

        assertFalse(authority.isValid(lease, attestation(), context(epoch = 8L)))
        assertFalse(presentation.isTransparent)
        assertFalse(presentation.canPresent(lease))
        assertFalse(authority.isValid(lease, attestation(), context(windowId = 18)))
        assertFalse(
            authority.isValid(
                lease,
                attestation(),
                context(viewport = ChromeVisualViewport(0, 0, 1080, 1200)),
            ),
        )
        assertNull(authority.mint(attestation(), context(packageName = "other.app")))
    }

    @Test
    fun `Chrome exit revokes and reentry requires a new capability`() {
        val first = assertNotNull(authority.mint(attestation(), context()))
        authority.revoke()
        assertFalse(authority.isValid(first, attestation(), context()))

        val second = assertNotNull(authority.mint(attestation(), context(epoch = 8L)))
        assertNotEquals(first.capabilityId, second.capabilityId)
        assertFalse(authority.isValid(first, attestation(), context(epoch = 8L)))
        assertTrue(authority.isValid(second, attestation(), context(epoch = 8L)))
    }

    private fun context(
        packageName: String = ChromePhotosDataPlaneLabContract.ChromePackage,
        windowId: Int = 17,
        epoch: Long = 7L,
        viewport: ChromeVisualViewport = ChromeVisualViewport(0, 0, 1080, 2200),
    ) = ChromePhotosDataPlaneLeaseContext(
        packageName = packageName,
        windowId = windowId,
        epoch = epoch,
        viewport = viewport,
    )

    private fun attestation(
        devBuild: Boolean = true,
        proxyHealthy: Boolean = true,
        policyConfirmed: Boolean = true,
        vpnConfirmed: Boolean = true,
        vpnSessionId: String = SessionId,
        fixtureConfirmed: Boolean = true,
        realWebScopeConfirmed: Boolean = false,
        heartbeatElapsed: Long = now,
        validUntilElapsed: Long = now + 1_000L,
        accessibilityBound: Boolean = true,
    ) = ChromePhotosDataPlaneAttestation(
        devBuild = devBuild,
        sessionId = SessionId,
        active = true,
        proxyHealthy = proxyHealthy,
        policyConfirmed = policyConfirmed,
        vpnConfirmed = vpnConfirmed,
        vpnSessionId = vpnSessionId,
        fixtureConfirmed = fixtureConfirmed,
        realWebScopeConfirmed = realWebScopeConfirmed,
        heartbeatElapsed = heartbeatElapsed,
        validUntilElapsed = validUntilElapsed,
        accessibilityBound = accessibilityBound,
    )

    private companion object {
        const val SessionId = "lab-session-a"
    }
}
