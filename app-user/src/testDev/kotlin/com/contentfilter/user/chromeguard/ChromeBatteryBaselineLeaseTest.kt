package com.contentfilter.user.chromeguard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeBatteryBaselineLeaseTest {
    @Test
    fun `valid snapshot requires every fail closed precondition and reset count three`() {
        val valid = validPreconditions()

        assertTrue(valid.valid)
        assertTrue(valid.rejectionReasons().isEmpty())

        assertEquals(listOf("lab_active"), valid.copy(labInactive = false).rejectionReasons())
        assertEquals(listOf("reset_count"), valid.copy(resetCount = 2).rejectionReasons())
        assertEquals(listOf("svg_registry"), valid.copy(svgRegistryClosed = false).rejectionReasons())
    }

    @Test
    fun `duration is bounded to one through forty five minutes`() {
        assertEquals(60_000L, chromeBatteryBaselineDurationMillis(1))
        assertEquals(45L * 60_000L, chromeBatteryBaselineDurationMillis(45))
        assertEquals(null, chromeBatteryBaselineDurationMillis(0))
        assertEquals(null, chromeBatteryBaselineDurationMillis(46))
    }

    @Test
    fun `lease binds boot monotonic issue and expiry`() {
        val lease = ChromeBatteryBaselineLease(bootMarker = 9L, issuedAtElapsed = 1_000L, expiresAtElapsed = 61_000L)

        assertTrue(lease.isCurrent(currentBootMarker = 9L, nowElapsed = 1_000L))
        assertTrue(lease.isCurrent(currentBootMarker = 9L, nowElapsed = 60_999L))
        assertFalse(lease.isCurrent(currentBootMarker = 9L, nowElapsed = 61_000L))
        assertFalse(lease.isCurrent(currentBootMarker = 10L, nowElapsed = 2_000L))
        assertFalse(
            lease.copy(expiresAtElapsed = 1_000L + ChromeBatteryBaselineLease.MaximumDurationMillis + 1L)
                .isCurrent(currentBootMarker = 9L, nowElapsed = 2_000L),
        )
    }

    private fun validPreconditions() =
        ChromeBatteryBaselinePreconditionSnapshot(
            devPackage = true,
            deviceOwner = true,
            chromePackageExact = true,
            chromeSuspended = true,
            labInactive = true,
            presentationNotReady = true,
            realWebAuthorityClosed = true,
            labProxyAbsent = true,
            globalProxyAbsent = true,
            ephemeralCaAbsent = true,
            fullTunnelAbsent = true,
            outstandingAuthorityTokensZero = true,
            svgRegistryClosed = true,
            resetCount = 3,
        )
}
