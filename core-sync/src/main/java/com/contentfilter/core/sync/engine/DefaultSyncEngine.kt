package com.contentfilter.core.sync.engine

import android.util.Log
import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.entity.SyncCursorEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.remote.RemoteAccountRepository
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseTable
import com.contentfilter.core.sync.outbox.OutboxProcessor
import javax.inject.Inject

class DefaultSyncEngine
    @Inject
    constructor(
        private val outboxProcessor: OutboxProcessor,
        private val accountRepository: RemoteAccountRepository,
        private val deviceRepository: RemoteDeviceRepository,
        private val policyRepository: RemotePolicyRepository,
        private val limitRepository: RemoteLimitRepository,
        private val requestRepository: RemoteRequestRepository,
        private val syncCursorDao: SyncCursorDao,
        private val applier: RoomRemoteApplier,
        private val systemStatusRepository: SystemStatusRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
    ) : SyncEngine {
        override suspend fun syncOnce(): SyncResult {
            markCurrentDeviceSeen()
            runCatching {
                outboxProcessor.processPending()
            }.onFailure { exception ->
                Log.e(LogTag, "Outbox processing crashed: ${exception.message}", exception)
                systemStatusRepository.updateSyncState(ComponentState.Warning)
                return SyncResult(success = false, message = "Sync deferred; outbox failed.")
            }
            return pullRemoteChanges().also { result ->
                systemStatusRepository.updateSyncState(
                    if (result.success) ComponentState.Enabled else ComponentState.Warning,
                )
            }
        }

        override suspend fun syncAccessRequestsFull(): SyncResult =
            when (val result = requestRepository.pullAccessRequests(updatedAfterIso = null)) {
                is RemoteResult.Failure -> {
                    Log.w(LogTag, "Full access_requests pull failed: ${result.reason}")
                    systemStatusRepository.updateSyncState(ComponentState.Warning)
                    SyncResult(success = false, message = result.reason)
                }
                is RemoteResult.Success -> {
                    runCatching {
                        applier.applyAccessRequests(result.value)
                        result.value.maxOfOrNull { it.updatedAt }?.let { cursor ->
                            syncCursorDao.upsert(
                                SyncCursorEntity(
                                    tableName = SupabaseTable.AccessRequests.tableName,
                                    updatedAfterIso = cursor,
                                    syncedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }.fold(
                        onSuccess = {
                            Log.i(LogTag, "Full access_requests pull applied count=${result.value.size}")
                            systemStatusRepository.updateSyncState(ComponentState.Enabled)
                            SyncResult(
                                success = true,
                                message = "Access requests synced: ${result.value.size}",
                            )
                        },
                        onFailure = { exception ->
                            Log.e(LogTag, "Full access_requests apply crashed: ${exception.message}", exception)
                            systemStatusRepository.updateSyncState(ComponentState.Warning)
                            SyncResult(success = false, message = "Access requests could not be saved locally.")
                        },
                    )
                }
            }

        override suspend fun syncCoreDataFull(): SyncResult {
            markCurrentDeviceSeen()
            runCatching {
                outboxProcessor.processPending()
            }.onFailure { exception ->
                Log.e(LogTag, "Full core sync outbox crashed: ${exception.message}", exception)
                systemStatusRepository.updateSyncState(ComponentState.Warning)
                return SyncResult(success = false, message = "Sync deferred; outbox failed.")
            }
            val result = pullCoreData(forceFull = true)
            systemStatusRepository.updateSyncState(
                if (result.success) ComponentState.Enabled else ComponentState.Warning,
            )
            return result
        }

        override suspend fun syncDevicesFull(): SyncResult =
            run {
                markCurrentDeviceSeen()
                syncDevicesFullInternal()
            }

        private suspend fun syncDevicesFullInternal(): SyncResult =
            when (val result = deviceRepository.pullDevices(updatedAfterIso = null)) {
                is RemoteResult.Failure -> {
                    Log.w(LogTag, "Full devices pull failed: ${result.reason}")
                    systemStatusRepository.updateSyncState(ComponentState.Warning)
                    SyncResult(success = false, message = result.reason)
                }
                is RemoteResult.Success -> {
                    runCatching {
                        applier.applyDevices(result.value)
                        result.value.maxOfOrNull { it.updatedAt }?.let { cursor ->
                            syncCursorDao.upsert(
                                SyncCursorEntity(
                                    tableName = SupabaseTable.Devices.tableName,
                                    updatedAfterIso = cursor,
                                    syncedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }.fold(
                        onSuccess = {
                            val activeCount = result.value.count { it.deletedAt == null }
                            Log.i(
                                LogTag,
                                "Full devices pull applied remote=${result.value.size} active=$activeCount",
                            )
                            systemStatusRepository.updateSyncState(ComponentState.Enabled)
                            SyncResult(
                                success = true,
                                message = "Dispositivos sincronizados: $activeCount",
                            )
                        },
                        onFailure = { exception ->
                            Log.e(LogTag, "Full devices apply crashed: ${exception.message}", exception)
                            systemStatusRepository.updateSyncState(ComponentState.Warning)
                            SyncResult(success = false, message = "No se pudieron guardar dispositivos.")
                        },
                    )
                }
            }

        override suspend fun syncRequestResultsFull(): SyncResult {
            val requests = syncAccessRequestsFull()
            val grants =
                when (val result = requestRepository.pullExtraTimeGrants(updatedAfterIso = null)) {
                    is RemoteResult.Failure -> {
                        Log.w(LogTag, "Full extra_time_grants pull failed: ${result.reason}")
                        systemStatusRepository.updateSyncState(ComponentState.Warning)
                        SyncResult(success = false, message = result.reason)
                    }
                    is RemoteResult.Success -> {
                        runCatching {
                            applier.applyExtraTimeGrants(result.value)
                            result.value.maxOfOrNull { it.updatedAt }?.let { cursor ->
                                syncCursorDao.upsert(
                                    SyncCursorEntity(
                                        tableName = SupabaseTable.ExtraTimeGrants.tableName,
                                        updatedAfterIso = cursor,
                                        syncedAtEpochMillis = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }.fold(
                            onSuccess = {
                                Log.i(LogTag, "Full extra_time_grants pull applied count=${result.value.size}")
                                systemStatusRepository.updateSyncState(ComponentState.Enabled)
                                SyncResult(
                                    success = true,
                                    message = "Extra time grants synced: ${result.value.size}",
                                )
                            },
                            onFailure = { exception ->
                                Log.e(LogTag, "Full extra_time_grants apply crashed: ${exception.message}", exception)
                                systemStatusRepository.updateSyncState(ComponentState.Warning)
                                SyncResult(success = false, message = "Extra time grants could not be saved locally.")
                            },
                        )
                    }
                }
            return SyncResult(
                success = requests.success && grants.success,
                message = "Requests: ${requests.message}; grants: ${grants.message}",
            )
        }

        private suspend fun markCurrentDeviceSeen() {
            val activation = deviceActivationRepository.currentActivation() ?: return
            val health = systemStatusRepository.currentHealth()
            when (val result = deviceRepository.markDeviceSeen(activation.deviceId, health)) {
                is RemoteResult.Success -> Unit
                is RemoteResult.Failure -> Log.w(LogTag, "Device heartbeat failed: ${result.reason}")
            }
        }

        private suspend fun pullRemoteChanges(): SyncResult {
            val results =
                pullCoreDataResults(forceFull = false) +
                    listOf(
                        pull(
                            table = SupabaseTable.AccessRequests,
                            request = { requestRepository.pullAccessRequests(cursorFor(SupabaseTable.AccessRequests)) },
                            apply = applier::applyAccessRequests,
                            updatedAt = { it.updatedAt },
                        ),
                        pull(
                            table = SupabaseTable.ExtraTimeGrants,
                            request = { requestRepository.pullExtraTimeGrants(cursorFor(SupabaseTable.ExtraTimeGrants)) },
                            apply = applier::applyExtraTimeGrants,
                            updatedAt = { it.updatedAt },
                        ),
                    )
            val success = results.all { it }
            return SyncResult(
                success = success,
                message = if (success) "Sync completed." else "Sync deferred; remote is unavailable.",
            )
        }

        private suspend fun pullCoreData(forceFull: Boolean): SyncResult {
            val success = pullCoreDataResults(forceFull).all { it }
            return SyncResult(
                success = success,
                message = if (success) "Core data synced." else "Core data sync deferred; remote is unavailable.",
            )
        }

        private suspend fun pullCoreDataResults(forceFull: Boolean): List<Boolean> =
            listOf(
                pull(
                    table = SupabaseTable.Accounts,
                    request = { accountRepository.pullAccounts(cursorFor(SupabaseTable.Accounts, forceFull)) },
                    apply = applier::applyAccounts,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.Devices,
                    request = { deviceRepository.pullDevices(cursorFor(SupabaseTable.Devices, forceFull)) },
                    apply = applier::applyDevices,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.Policies,
                    request = { policyRepository.pullPolicies(cursorFor(SupabaseTable.Policies, forceFull)) },
                    apply = applier::applyPolicies,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.PolicyRules,
                    request = { policyRepository.pullPolicyRules(cursorFor(SupabaseTable.PolicyRules, forceFull)) },
                    apply = applier::applyPolicyRules,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.DailyLimits,
                    request = { limitRepository.pullDailyLimits(cursorFor(SupabaseTable.DailyLimits, forceFull)) },
                    apply = applier::applyDailyLimits,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.AppGroups,
                    request = { limitRepository.pullAppGroups(cursorFor(SupabaseTable.AppGroups, forceFull)) },
                    apply = applier::applyAppGroups,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.AppGroupApps,
                    request = { limitRepository.pullAppGroupApps(cursorFor(SupabaseTable.AppGroupApps, forceFull)) },
                    apply = applier::applyAppGroupApps,
                    updatedAt = { it.updatedAt },
                ),
            )

        private suspend fun cursorFor(table: SupabaseTable): String? =
            syncCursorDao.cursorFor(table.tableName)?.updatedAfterIso

        private suspend fun cursorFor(
            table: SupabaseTable,
            forceFull: Boolean,
        ): String? = if (forceFull) null else cursorFor(table)

        private suspend fun <T> pull(
            table: SupabaseTable,
            request: suspend () -> RemoteResult<List<T>>,
            apply: suspend (List<T>) -> Unit,
            updatedAt: (T) -> String,
        ): Boolean {
            return runCatching {
                when (val result = request()) {
                    is RemoteResult.Failure -> {
                        Log.w(LogTag, "Pull failed table=${table.tableName}: ${result.reason}")
                        false
                    }
                    is RemoteResult.Success -> {
                        apply(result.value)
                        if (result.value.isNotEmpty()) {
                            Log.i(LogTag, "Pulled ${result.value.size} remote rows from ${table.tableName}")
                        }
                        result.value.maxOfOrNull(updatedAt)?.let { cursor ->
                            syncCursorDao.upsert(
                                SyncCursorEntity(
                                    tableName = table.tableName,
                                    updatedAfterIso = cursor,
                                    syncedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                        true
                    }
                }
            }.getOrElse { exception ->
                Log.e(LogTag, "Pull crashed table=${table.tableName}: ${exception.message}", exception)
                false
            }
        }

        private companion object {
            const val LogTag = "SyncEngine"
        }
    }
