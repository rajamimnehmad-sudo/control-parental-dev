package com.contentfilter.user.protection

import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ProtectionControlRepository
import com.contentfilter.core.domain.repository.ProtectionStateStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionControlCoordinator
    @Inject
    constructor(
        private val activationRepository: DeviceActivationRepository,
        private val remoteRepository: ProtectionControlRepository,
        private val stateStore: ProtectionStateStore,
    ) {
        suspend fun refresh(): Result<Unit> {
            val activation = activationRepository.currentActivation() ?: return Result.success(Unit)
            val control =
                remoteRepository.get(activation.deviceId).getOrElse {
                    return Result.failure(it)
                } ?: return Result.success(Unit)
            stateStore.saveControl(control)
            val consumedRevision = stateStore.pendingRecoveryConsumedRevision()
            return remoteRepository
                .acknowledge(
                    deviceId = activation.deviceId,
                    commandRevision = control.commandRevision,
                    recoveryConsumedRevision = consumedRevision,
                ).onSuccess {
                    consumedRevision?.let(stateStore::markRecoveryConsumptionAcknowledged)
                }
        }
    }
