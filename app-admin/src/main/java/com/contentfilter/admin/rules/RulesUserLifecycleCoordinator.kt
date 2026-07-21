package com.contentfilter.admin.rules

import android.util.Log
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.InstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseActivationClient
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.SyncResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal data class PairingTokenResult(
    val code: String,
    val expiresAt: String,
)

internal enum class ArchiveLocalRepairStep {
    Device,
    InstalledApps,
    FullSync,
}

internal sealed interface ArchiveUserResult {
    data object CompleteSuccess : ArchiveUserResult

    data class RemoteSuccessWithLocalRepairPending(
        val pendingSteps: Set<ArchiveLocalRepairStep>,
    ) : ArchiveUserResult

    data class RemoteFailure(
        val reason: String,
    ) : ArchiveUserResult
}

internal class RulesUserArchiveCoordinator private constructor(
    private val archiveRemote: suspend (String) -> RemoteResult<Unit>,
    private val deleteDevice: suspend (String) -> Unit,
    private val deleteInstalledApps: suspend (String) -> Unit,
    private val syncDevices: suspend () -> SyncResult,
    private val logResult: (String) -> Unit,
) {
    @Inject
    constructor(
        activationClient: SupabaseActivationClient,
        deviceRepository: DeviceRepository,
        installedAppRepository: InstalledAppRepository,
        syncEngine: SyncEngine,
    ) : this(
        archiveRemote = activationClient::archiveProtectedUser,
        deleteDevice = deviceRepository::deleteDevice,
        deleteInstalledApps = installedAppRepository::deleteForDevice,
        syncDevices = syncEngine::syncDevicesFull,
        logResult = { Log.i(LogTag, it) },
    )

    suspend fun archiveUser(deviceId: String): ArchiveUserResult {
        val remoteResult =
            try {
                archiveRemote(deviceId)
            } catch (error: Throwable) {
                return ArchiveUserResult.RemoteFailure(error.message ?: "Error remoto inesperado")
            }
        return when (remoteResult) {
            is RemoteResult.Failure -> ArchiveUserResult.RemoteFailure(remoteResult.reason)
            is RemoteResult.Success -> repairAfterRemoteArchive(deviceId)
        }
    }

    private suspend fun repairAfterRemoteArchive(deviceId: String): ArchiveUserResult {
        val pendingSteps = mutableSetOf<ArchiveLocalRepairStep>()
        runCatching { deleteDevice(deviceId) }
            .onFailure { pendingSteps += ArchiveLocalRepairStep.Device }
        runCatching { deleteInstalledApps(deviceId) }
            .onFailure { pendingSteps += ArchiveLocalRepairStep.InstalledApps }
        val syncResult =
            runCatching { syncDevices() }
                .onFailure { pendingSteps += ArchiveLocalRepairStep.FullSync }
                .getOrNull()
        if (syncResult?.success == false) pendingSteps += ArchiveLocalRepairStep.FullSync
        logResult(
            "archiveUser remoteSuccess deviceId=$deviceId syncSuccess=${syncResult?.success} " +
                "pendingLocalRepair=$pendingSteps message=${syncResult?.message.orEmpty()}",
        )
        return if (pendingSteps.isEmpty()) {
            ArchiveUserResult.CompleteSuccess
        } else {
            ArchiveUserResult.RemoteSuccessWithLocalRepairPending(pendingSteps)
        }
    }

    internal companion object {
        fun forTest(
            archiveRemote: suspend (String) -> RemoteResult<Unit>,
            deleteDevice: suspend (String) -> Unit,
            deleteInstalledApps: suspend (String) -> Unit,
            syncDevices: suspend () -> SyncResult,
            logResult: (String) -> Unit = {},
        ): RulesUserArchiveCoordinator =
            RulesUserArchiveCoordinator(
                archiveRemote = archiveRemote,
                deleteDevice = deleteDevice,
                deleteInstalledApps = deleteInstalledApps,
                syncDevices = syncDevices,
                logResult = logResult,
            )
    }
}

internal class RulesUserLifecycleCoordinator
    @Inject
    constructor(
        private val activationClient: SupabaseActivationClient,
        private val archiveCoordinator: RulesUserArchiveCoordinator,
    ) {
        suspend fun listArchivedUsers(): Result<List<ArchivedUserUiState>> =
            runCatching {
                when (val result = activationClient.listArchivedProtectedUsers()) {
                    is RemoteResult.Success ->
                        result.value.map { archived ->
                            ArchivedUserUiState(
                                archiveId = archived.archiveId,
                                deviceId = archived.deviceId,
                                name = archived.displayName,
                                archivedAtLabel = archived.archivedAt.archivedAtLabel(),
                                canRestore = archived.canRestore,
                            )
                        }
                    is RemoteResult.Failure -> error(result.reason)
                }
            }

        suspend fun createRestoreCode(archiveId: String): Result<PairingTokenResult> =
            runCatching {
                when (
                    val result =
                        activationClient.createArchivedUserRestoreCode(
                            archiveId = archiveId,
                            ttlMinutes = UserPairingTokenTtlMinutes,
                        )
                ) {
                    is RemoteResult.Success -> PairingTokenResult(result.value.code, result.value.expiresAt)
                    is RemoteResult.Failure -> error(result.reason)
                }
            }

        suspend fun createPairingCode(): Result<PairingTokenResult> =
            runCatching {
                when (val result = activationClient.createDevicePairingCode(UserPairingTokenTtlMinutes)) {
                    is RemoteResult.Success -> PairingTokenResult(result.value.code, result.value.expiresAt)
                    is RemoteResult.Failure -> error(result.reason)
                }
            }

        suspend fun createRelinkCode(deviceId: String): Result<PairingTokenResult> =
            runCatching {
                when (val result = activationClient.createDeviceRelinkCode(deviceId, RelinkTokenTtlMinutes)) {
                    is RemoteResult.Success -> PairingTokenResult(result.value.code, result.value.expiresAt)
                    is RemoteResult.Failure -> error(result.reason)
                }
            }

        suspend fun archiveUser(deviceId: String): ArchiveUserResult = archiveCoordinator.archiveUser(deviceId)
    }

internal fun String.withPairingName(userName: String): String {
    val safeName =
        userName
            .trim()
            .replace(Regex("\\s+"), "-")
            .trim('-')
            .take(32)
    return "${safeName.ifBlank { "usuario" }}-${trim()}"
}

private fun String.archivedAtLabel(): String =
    runCatching { ArchiveDateFormatter.format(Instant.parse(this)) }
        .getOrDefault("Fecha no disponible")

private val ArchiveDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.of("America/Argentina/Buenos_Aires"))

private const val UserPairingTokenTtlMinutes = 180
private const val RelinkTokenTtlMinutes = 30
