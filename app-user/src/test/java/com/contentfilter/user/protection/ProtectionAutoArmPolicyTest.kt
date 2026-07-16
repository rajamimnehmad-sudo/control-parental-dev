package com.contentfilter.user.protection

import com.contentfilter.core.domain.model.DeviceProtectionControl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectionAutoArmPolicyTest {
    @Test
    fun untouchedControlCanBeAutomaticallyArmed() {
        assertTrue(DeviceProtectionControl(deviceId = "device", accountId = "account").canBeAutomaticallyArmed())
    }

    @Test
    fun explicitlyDisarmedControlIsNeverAutomaticallyRearmed() {
        assertFalse(
            DeviceProtectionControl(
                deviceId = "device",
                accountId = "account",
                armed = false,
                commandRevision = 2,
            ).canBeAutomaticallyArmed(),
        )
    }

    @Test
    fun armedOrMissingControlNeedsNoAutomaticMutation() {
        assertFalse(
            DeviceProtectionControl(
                deviceId = "device",
                accountId = "account",
                armed = true,
            ).canBeAutomaticallyArmed(),
        )
        assertFalse(null.canBeAutomaticallyArmed())
    }
}
