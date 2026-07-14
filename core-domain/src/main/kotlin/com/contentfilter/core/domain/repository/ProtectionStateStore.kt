package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryUnlockResult

interface ProtectionStateStore {
    fun currentControl(): DeviceProtectionControl?

    fun saveControl(control: DeviceProtectionControl)

    fun isArmed(): Boolean

    fun isAuthorized(
        scope: ProtectionAuthorizationScope,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean

    fun verifyAndConsumeRecovery(
        code: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RecoveryUnlockResult

    fun pendingRecoveryConsumedRevision(): Long?

    fun markRecoveryConsumptionAcknowledged(revision: Long)

    fun cancelLocalRemovalAuthorization()
}
