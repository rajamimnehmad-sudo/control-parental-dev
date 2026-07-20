package com.contentfilter.core.security

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.RecoveryCodeVerifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoveryMaterialChangeTest {
    @Test
    fun ordinaryControlRefreshPreservesAttemptState() {
        val previous = control(revision = 3, appliedRevision = 2)
        val refreshed = previous.copy(appliedRevision = 3, commandRevision = 3)

        assertFalse(recoveryMaterialChanged(previous, refreshed))
    }

    @Test
    fun rotatingRecoveryResetsAttemptState() {
        val previous = control(revision = 3)

        assertTrue(recoveryMaterialChanged(previous, control(revision = 4, salt = "new-salt")))
    }

    @Test
    fun firstControlStartsWithCleanAttemptState() {
        assertTrue(recoveryMaterialChanged(null, control(revision = 1)))
    }

    @Test
    fun rotatingOfflineKitResetsAttemptState() {
        val previous = control(revision = 3)
        val kit = listOf(RecoveryCodeVerifier(slot = 0, salt = "kit-salt", verifier = "kit-verifier"))

        assertTrue(recoveryMaterialChanged(previous, previous.copy(recoveryKitRevision = 1, recoveryKit = kit)))
    }

    private fun control(
        revision: Long,
        salt: String = "salt",
        appliedRevision: Long = 0,
    ): DeviceProtectionControl =
        DeviceProtectionControl(
            deviceId = "device",
            accountId = "account",
            commandRevision = revision,
            appliedRevision = appliedRevision,
            recoverySalt = salt,
            recoveryVerifier = "verifier",
            recoveryRevision = revision,
        )
}
