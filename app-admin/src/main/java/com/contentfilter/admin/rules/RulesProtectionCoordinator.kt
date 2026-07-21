package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryCodeVerifier
import com.contentfilter.core.domain.repository.ProtectionControlRepository
import com.contentfilter.core.security.AdminRecoveryKitStore
import com.contentfilter.core.security.RecoveryCodeHasher
import javax.inject.Inject

internal data class ProtectionControlsSnapshot(
    val controls: Map<String, DeviceProtectionControl>,
    val remainingCodes: Map<String, Int>,
)

internal sealed interface RecoveryKitResult {
    data class CodeRevealed(
        val code: String,
        val remaining: Int,
    ) : RecoveryKitResult

    data class KitPrepared(
        val control: DeviceProtectionControl,
        val remaining: Int,
    ) : RecoveryKitResult
}

internal class RulesProtectionCoordinator
    @Inject
    constructor(
        private val protectionControlRepository: ProtectionControlRepository,
        private val recoveryCodeHasher: RecoveryCodeHasher,
        private val adminRecoveryKitStore: AdminRecoveryKitStore,
    ) {
        suspend fun setArmed(
            accountId: String,
            deviceId: String,
            armed: Boolean,
        ): Result<DeviceProtectionControl> = protectionControlRepository.setArmed(accountId, deviceId, armed)

        suspend fun authorizeRemoval(
            accountId: String,
            deviceId: String,
        ): Result<DeviceProtectionControl> =
            protectionControlRepository.authorize(
                accountId = accountId,
                deviceId = deviceId,
                scope = ProtectionAuthorizationScope.Removal,
                durationMinutes = 30,
            )

        suspend fun revealOrPrepareRecoveryKit(
            accountId: String,
            deviceId: String,
            expectedRevision: Long?,
        ): Result<RecoveryKitResult> =
            runCatching {
                if (adminRecoveryKitStore.remaining(deviceId, expectedRevision) > 0) {
                    val revealed = checkNotNull(adminRecoveryKitStore.revealNext(deviceId, expectedRevision))
                    RecoveryKitResult.CodeRevealed(revealed.code, revealed.remaining)
                } else {
                    val materials = List(RecoveryKitSize) { recoveryCodeHasher.generate() }
                    val control =
                        protectionControlRepository
                            .rotateRecoveryKit(
                                accountId = accountId,
                                deviceId = deviceId,
                                verifiers =
                                    materials.mapIndexed { slot, material ->
                                        RecoveryCodeVerifier(slot, material.salt, material.verifier)
                                    },
                            ).getOrThrow()
                    adminRecoveryKitStore.save(
                        deviceId = deviceId,
                        revision = control.recoveryKitRevision,
                        codes = materials.map { it.code },
                    )
                    RecoveryKitResult.KitPrepared(control, RecoveryKitSize)
                }
            }

        suspend fun refresh(deviceIds: List<String>): ProtectionControlsSnapshot {
            val controls =
                deviceIds.distinct().mapNotNull { deviceId ->
                    protectionControlRepository.get(deviceId).getOrNull()?.let { deviceId to it }
                }.toMap()
            return ProtectionControlsSnapshot(
                controls = controls,
                remainingCodes =
                    controls.mapValues { (deviceId, control) ->
                        adminRecoveryKitStore.remaining(
                            deviceId,
                            control.recoveryKitRevision.takeIf { it > 0 },
                        )
                    },
            )
        }

        private companion object {
            const val RecoveryKitSize = 5
        }
    }
