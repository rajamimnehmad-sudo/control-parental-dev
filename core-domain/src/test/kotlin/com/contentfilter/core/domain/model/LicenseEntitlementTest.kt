package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseEntitlementTest {
    @Test
    fun `device clock cannot expire an active server entitlement`() {
        val entitlement =
            entitlement(
                state = LicenseState.Active,
                startsAt = 1_000,
                expiresAt = 10 * DayMillis,
                verifiedAt = 2_000,
            )

        assertEquals(LicenseState.Active, entitlement.effectiveState())
    }

    @Test
    fun `scheduled entitlement requires a new server verification to activate`() {
        val scheduled =
            entitlement(
                state = LicenseState.Scheduled,
                startsAt = 5_000,
                expiresAt = null,
                verifiedAt = 4_999,
            )
        val activated =
            entitlement(
                state = LicenseState.Active,
                startsAt = 5_000,
                expiresAt = null,
                verifiedAt = 5_000,
            )

        assertEquals(LicenseState.Scheduled, scheduled.effectiveState())
        assertEquals(LicenseState.Active, activated.effectiveState())
    }

    @Test
    fun `manual suspension has priority over dates`() {
        val entitlement =
            entitlement(
                state = LicenseState.Suspended,
                startsAt = 1_000,
                expiresAt = 10_000,
                verifiedAt = 2_000,
            )

        assertEquals(LicenseState.Suspended, entitlement.effectiveState())
    }

    @Test
    fun `expiring soon uses server evaluated time`() {
        val entitlement =
            entitlement(
                state = LicenseState.Active,
                startsAt = 1_000,
                expiresAt = 8 * DayMillis,
                verifiedAt = 2 * DayMillis,
            )

        assertEquals(LicenseState.ExpiringSoon, entitlement.effectiveState())
    }

    @Test
    fun `only paid or grace states allow protection`() {
        assertTrue(LicenseState.Active.allowsProtection())
        assertTrue(LicenseState.ExpiringSoon.allowsProtection())
        assertTrue(LicenseState.GracePeriod.allowsProtection())
        assertFalse(LicenseState.Scheduled.allowsProtection())
        assertFalse(LicenseState.Expired.allowsProtection())
        assertFalse(LicenseState.Suspended.allowsProtection())
        assertFalse(LicenseState.PendingActivation.allowsProtection())
    }

    private fun entitlement(
        state: LicenseState,
        startsAt: Long?,
        expiresAt: Long?,
        verifiedAt: Long,
    ) = LicenseEntitlement(
        state = state,
        startsAtEpochMillis = startsAt,
        expiresAtEpochMillis = expiresAt,
        verifiedAtEpochMillis = verifiedAt,
    )

    private companion object {
        const val DayMillis = 24L * 60 * 60 * 1_000
    }
}
