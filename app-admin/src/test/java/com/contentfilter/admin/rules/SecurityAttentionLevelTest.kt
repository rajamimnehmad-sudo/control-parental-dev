package com.contentfilter.admin.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityAttentionLevelTest {
    @Test
    fun `healthy verified device has no badge`() {
        val device =
            device(
                status = UserDeviceStatus.Active,
                protectionComplete = true,
            )

        assertEquals(SecurityAttentionLevel.None, device.securityAttentionLevel())
    }

    @Test
    fun `unknown or long offline device uses warning`() {
        assertEquals(
            SecurityAttentionLevel.Warning,
            device(status = UserDeviceStatus.Unknown, protectionVerificationPending = true).securityAttentionLevel(),
        )
        assertEquals(
            SecurityAttentionLevel.Warning,
            device(status = UserDeviceStatus.Inactive).securityAttentionLevel(),
        )
    }

    @Test
    fun `confirmed disabled protection or possible uninstall uses critical`() {
        assertEquals(
            SecurityAttentionLevel.Critical,
            device(confirmedProtectionFailure = true).securityAttentionLevel(),
        )
        assertEquals(
            SecurityAttentionLevel.Critical,
            device(possibleUninstall = true, protectionVerificationPending = true).securityAttentionLevel(),
        )
    }

    private fun device(
        status: UserDeviceStatus = UserDeviceStatus.Active,
        protectionComplete: Boolean = false,
        possibleUninstall: Boolean = false,
        confirmedProtectionFailure: Boolean = false,
        protectionVerificationPending: Boolean = false,
    ) = UserDeviceUiState(
        id = "device",
        accountId = "account",
        name = "Usuario",
        status = status,
        lastSeenLabel = "ahora",
        appCount = 1,
        protectionComplete = protectionComplete,
        possibleUninstall = possibleUninstall,
        confirmedProtectionFailure = confirmedProtectionFailure,
        protectionVerificationPending = protectionVerificationPending,
    )
}
