package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceProtectionControlRecoveryKitTest {
    @Test
    fun `kit remains available while one slot is unused`() {
        val control =
            DeviceProtectionControl(
                deviceId = "device",
                accountId = "account",
                recoveryKitRevision = 1,
                recoveryKit =
                    listOf(
                        RecoveryCodeVerifier(0, "salt-0", "verifier-0"),
                        RecoveryCodeVerifier(1, "salt-1", "verifier-1"),
                    ),
                recoveryConsumedSlots = setOf(0),
            )

        assertTrue(control.hasAvailableRecovery)
        assertFalse(control.copy(recoveryConsumedSlots = setOf(0, 1)).hasAvailableRecovery)
    }
}
