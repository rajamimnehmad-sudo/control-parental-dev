package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryCodeVerifier

interface ProtectionControlRepository {
    suspend fun get(deviceId: String): Result<DeviceProtectionControl?>

    suspend fun autoArm(deviceId: String): Result<Unit>

    suspend fun setArmed(
        accountId: String,
        deviceId: String,
        armed: Boolean,
    ): Result<DeviceProtectionControl>

    suspend fun authorize(
        accountId: String,
        deviceId: String,
        scope: ProtectionAuthorizationScope,
        durationMinutes: Long = 10,
    ): Result<DeviceProtectionControl>

    suspend fun rotateRecovery(
        accountId: String,
        deviceId: String,
        salt: String,
        verifier: String,
    ): Result<DeviceProtectionControl>

    suspend fun rotateRecoveryKit(
        accountId: String,
        deviceId: String,
        verifiers: List<RecoveryCodeVerifier>,
    ): Result<DeviceProtectionControl>

    suspend fun acknowledge(
        deviceId: String,
        commandRevision: Long,
        recoveryConsumedRevision: Long? = null,
        recoveryKitRevision: Long? = null,
        recoveryConsumedSlots: Set<Int> = emptySet(),
    ): Result<Unit>

    suspend fun cancelOwnRemovalAuthorization(): Result<Unit>
}
