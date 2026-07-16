package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryUnlockResult
import kotlinx.coroutines.flow.Flow

interface ProtectionStateStore {
    fun observeControl(): Flow<DeviceProtectionControl?>

    fun currentControl(): DeviceProtectionControl?

    fun saveControl(control: DeviceProtectionControl)

    fun isArmed(): Boolean

    fun isAuthorized(
        scope: ProtectionAuthorizationScope,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean

    fun authorizationExpiresAtEpochMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long?

    fun verifyAndConsumeRecovery(
        code: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RecoveryUnlockResult

    fun pendingRecoveryConsumedRevision(): Long?

    fun markRecoveryConsumptionAcknowledged(revision: Long)

    fun cancelLocalRemovalAuthorization()
}
