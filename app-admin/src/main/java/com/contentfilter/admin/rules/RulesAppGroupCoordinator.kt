package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.PolicyFastSyncResult
import com.contentfilter.core.sync.engine.SyncEngine
import java.util.UUID
import javax.inject.Inject

internal data class AppGroupDraft(
    val id: String?,
    val deviceId: String,
    val name: String,
    val limitMinutes: Int?,
    val packages: List<String>,
)

internal fun AppGroupDraft.validationMessage(existingGroups: List<AppGroupUiState>): String? {
    val duplicateName = existingGroups.firstOrNull { it.id != id && it.name.equals(name, ignoreCase = true) }
    val packageInOtherGroup =
        existingGroups.firstOrNull { group ->
            group.id != id && group.appPackages.any { it in packages }
        }
    return when {
        name.isBlank() -> "Ingresá un nombre para el grupo."
        duplicateName != null -> "Ya existe un grupo con ese nombre."
        limitMinutes == null || limitMinutes <= 0 -> "Ingresá minutos válidos para el grupo."
        packages.isEmpty() -> "Elegí al menos una app para el grupo."
        packageInOtherGroup != null ->
            "Una app elegida ya está en ${packageInOtherGroup.name}. Sacala de ese grupo o editá ese grupo."
        else -> null
    }
}

internal class RulesAppGroupCoordinator
    @Inject
    constructor(
        private val appGroupRepository: AppGroupRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) {
        suspend fun save(draft: AppGroupDraft): Result<PolicyFastSyncResult> =
            runCatching {
                val group =
                    AppGroup(
                        id = draft.id ?: UUID.randomUUID().toString(),
                        deviceId = draft.deviceId,
                        name = draft.name,
                        color = "teal",
                        limitMinutes = checkNotNull(draft.limitMinutes),
                        resetMinuteOfDay = NoonMinuteOfDay,
                        enabled = true,
                    )
                val receipt =
                    appGroupRepository.replaceGroupApps(group, draft.packages, "group-${UUID.randomUUID()}")
                syncScheduler.requestSync()
                syncEngine.syncPolicyChanges(receipt)
            }

        suspend fun delete(
            group: AppGroupUiState,
            deviceId: String,
        ): Result<PolicyFastSyncResult> =
            runCatching {
                val receipt =
                    appGroupRepository.deleteGroup(
                        AppGroup(
                            id = group.id,
                            deviceId = deviceId,
                            name = group.name,
                            color = "teal",
                            limitMinutes = group.limitMinutes,
                            resetMinuteOfDay = NoonMinuteOfDay,
                            enabled = false,
                        ),
                        "group-delete-${UUID.randomUUID()}",
                    )
                syncScheduler.requestSync()
                syncEngine.syncPolicyChanges(receipt)
            }

        private companion object {
            const val NoonMinuteOfDay = 720
        }
    }
