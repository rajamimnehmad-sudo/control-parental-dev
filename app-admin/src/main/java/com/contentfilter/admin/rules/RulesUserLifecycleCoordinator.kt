package com.contentfilter.admin.rules

import android.util.Log
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.InstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseActivationClient
import com.contentfilter.core.sync.engine.SyncEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal data class PairingTokenResult(
    val code: String,
    val expiresAt: String,
)

internal class RulesUserLifecycleCoordinator
    @Inject
    constructor(
        private val activationClient: SupabaseActivationClient,
        private val deviceRepository: DeviceRepository,
        private val installedAppRepository: InstalledAppRepository,
        private val syncEngine: SyncEngine,
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

        suspend fun archiveUser(deviceId: String): Result<Unit> =
            runCatching {
                when (val result = activationClient.archiveProtectedUser(deviceId)) {
                    is RemoteResult.Success -> {
                        deviceRepository.deleteDevice(deviceId)
                        installedAppRepository.deleteForDevice(deviceId)
                        val syncResult = syncEngine.syncDevicesFull()
                        Log.i(
                            LogTag,
                            "archiveUser finished deviceId=$deviceId syncSuccess=${syncResult.success} " +
                                "message=${syncResult.message}",
                        )
                    }
                    is RemoteResult.Failure -> error(result.reason)
                }
            }
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
