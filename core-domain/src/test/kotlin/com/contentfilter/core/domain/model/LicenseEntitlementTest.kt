package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseEntitlementTest {
    @Test
    fun `active entitlement expires locally without another server response`() {
        val entitlement = entitlement(LicenseState.Active, startsAt = 1_000, expiresAt = 10 * DayMillis)

        assertEquals(LicenseState.Active, entitlement.effectiveState(2_000))
        assertEquals(LicenseState.Expired, entitlement.effectiveState(10 * DayMillis))
    }

    @Test
    fun `scheduled entitlement activates locally at its start`() {
        val entitlement = entitlement(LicenseState.Scheduled, startsAt = 5_000, expiresAt = null)

        assertEquals(LicenseState.Scheduled, entitlement.effectiveState(4_999))
        assertEquals(LicenseState.Active, entitlement.effectiveState(5_000))
    }

    @Test
    fun `manual suspension has priority over dates`() {
        val entitlement = entitlement(LicenseState.Suspended, startsAt = 1_000, expiresAt = 10_000)

        assertEquals(LicenseState.Suspended, entitlement.effectiveState(2_000))
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
    ) = LicenseEntitlement(
        state = state,
        startsAtEpochMillis = startsAt,
        expiresAtEpochMillis = expiresAt,
        verifiedAtEpochMillis = 1_000,
    )

    private companion object {
        const val DayMillis = 24L * 60 * 60 * 1_000
    }
}
