package com.contentfilter.admin.rules

import android.util.Log
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.InstalledAppRepository
import com.contentfilter.core.domain.repository.TelemetryRepository
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.SyncResult
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal data class DeviceMessageToken(
    val deviceId: String?,
    val generation: Long,
)

internal class RulesMessageCoordinator
    @Inject
    constructor() {
        private var generation = 0L

        fun selectionChanged() {
            generation += 1
        }

        fun capture(deviceId: String?): DeviceMessageToken = DeviceMessageToken(deviceId, generation)

        fun isCurrent(
            token: DeviceMessageToken,
            selectedDeviceId: String?,
        ): Boolean = token.generation == generation && token.deviceId == selectedDeviceId
    }

internal data class DeviceOperationKey(
    val deviceId: String,
    val operation: String,
    val target: String = "",
)

internal class RulesOperationTracker
    @Inject
    constructor() {
        private var nextRequestId = 0L
        private val currentRequests = mutableMapOf<DeviceOperationKey, Long>()

        @Synchronized
        fun begin(key: DeviceOperationKey): Long {
            val requestId = ++nextRequestId
            currentRequests[key] = requestId
            return requestId
        }

        @Synchronized
        fun beginIfIdle(key: DeviceOperationKey): Long? {
            if (key in currentRequests) return null
            return begin(key)
        }

        @Synchronized
        fun isCurrent(
            key: DeviceOperationKey,
            requestId: Long,
        ): Boolean = currentRequests[key] == requestId

        @Synchronized
        fun finish(
            key: DeviceOperationKey,
            requestId: Long,
        ): Boolean {
            if (!isCurrent(key, requestId)) return false
            currentRequests.remove(key)
            return true
        }

        @Synchronized
        fun invalidate(key: DeviceOperationKey) {
            currentRequests.remove(key)
        }
    }

internal data class TrackedOperationResult<T>(
    val result: Result<T>,
    val wasCurrent: Boolean,
)

internal suspend fun <T> RulesOperationTracker.runTrackedOperation(
    key: DeviceOperationKey,
    requestId: Long,
    onFinished: (wasCurrent: Boolean) -> Unit,
    operation: suspend () -> T,
): TrackedOperationResult<T> {
    var result: Result<T>? = null
    var wasCurrent = false
    try {
        result =
            try {
                Result.success(operation())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
    } finally {
        wasCurrent = finish(key, requestId)
        onFinished(wasCurrent)
    }
    return TrackedOperationResult(
        result = checkNotNull(result),
        wasCurrent = wasCurrent,
    )
}

internal sealed interface InstalledAppsRefreshResult {
    data object Success : InstalledAppsRefreshResult

    data class Failure(val reason: String) : InstalledAppsRefreshResult
}

internal class RulesInstalledAppsLoader
    @Inject
    constructor(
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val installedAppRepository: InstalledAppRepository,
    ) {
        suspend fun refresh(
            deviceId: String,
            forceFull: Boolean,
            cachedCount: Int,
            requestId: Long,
        ): InstalledAppsRefreshResult {
            val startedAt = System.currentTimeMillis()
            if (BuildConfig.DEBUG) {
                Log.i(
                    LogTag,
                    "appsRefresh stage=cache requestId=apps-$requestId deviceId=${deviceId.safeDeviceId()} " +
                        "count=$cachedCount durationMs=${System.currentTimeMillis() - startedAt}",
                )
            }
            val updatedAfter =
                if (forceFull) {
                    null
                } else {
                    installedAppRepository.latestUpdatedAt(deviceId)?.let {
                        Instant.ofEpochMilli(it).toString()
                    }
                }
            val remoteStartedAt = System.currentTimeMillis()
            return when (
                val result =
                    remoteInstalledAppRepository.pullInstalledApps(
                        deviceId = deviceId,
                        updatedAfterIso = updatedAfter,
                    )
            ) {
                is RemoteResult.Success -> {
                    val remoteApps = result.value.map(RemoteInstalledAppDto::toInstalledApp)
                    installedAppRepository.mergeInstalledApps(remoteApps)
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            LogTag,
                            "appsRefresh stage=remote-merge requestId=apps-$requestId " +
                                "deviceId=${deviceId.safeDeviceId()} deltaCount=${remoteApps.size} " +
                                "forceFull=$forceFull remoteDurationMs=${System.currentTimeMillis() - remoteStartedAt} " +
                                "totalDurationMs=${System.currentTimeMillis() - startedAt}",
                        )
                    }
                    InstalledAppsRefreshResult.Success
                }
                is RemoteResult.Failure -> {
                    Log.w(LogTag, "appsRefresh result=failure reason=${result.reason}")
                    InstalledAppsRefreshResult.Failure(result.reason)
                }
            }
        }
    }

internal class RulesSyncCoordinator
    @Inject
    constructor(
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        private val telemetryRepository: TelemetryRepository,
    ) {
        fun requestSync() = syncScheduler.requestSync()

        suspend fun syncNowWithResult(): SyncResult = syncEngine.syncCoreDataFull()

        suspend fun recordAdminDiagnostic(
            action: String,
            deviceId: String,
            requestId: Long,
            previousState: String,
            requestedState: String,
            result: String,
            reason: String,
        ) {
            if (BuildConfig.FLAVOR != "dev") return
            telemetryRepository.record(
                TechnicalDiagnostic(
                    id = UUID.randomUUID().toString(),
                    type = "admin-rules",
                    message =
                        "layer=admin action=$action requestId=$requestId deviceId=${deviceId.safeDeviceId()} " +
                            "previousState=${previousState.take(MaxDiagnosticValueLength)} " +
                            "requestedState=${requestedState.take(MaxDiagnosticValueLength)} " +
                            "result=$result reason=${reason.take(MaxDiagnosticValueLength)}",
                    occurredAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

private fun RemoteInstalledAppDto.toInstalledApp(): InstalledApp =
    InstalledApp(
        id = id,
        accountId = accountId,
        deviceId = deviceId,
        appName = appName,
        packageName = packageName,
        versionName = versionName,
        isSystemApp = isSystemApp,
        iconBase64 = iconBase64,
        updatedAtEpochMillis = runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrDefault(0L),
    )

internal fun String.safeDeviceId(): String = take(8)
