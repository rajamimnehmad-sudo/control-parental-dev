package com.contentfilter.core.sync.engine

import android.util.Log
import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.entity.SyncCursorEntity
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
        private val deviceRepository: RemoteDeviceRepository,
        private val policyRepository: RemotePolicyRepository,
        private val limitRepository: RemoteLimitRepository,
        private val requestRepository: RemoteRequestRepository,
        private val syncCursorDao: SyncCursorDao,
        private val applier: RoomRemoteApplier,
    ) : SyncEngine {
        override suspend fun syncOnce(): SyncResult {
            runCatching {
                outboxProcessor.processPending()
            }.onFailure { exception ->
                Log.e(LogTag, "Outbox processing crashed: ${exception.message}", exception)
                return SyncResult(success = false, message = "Sync deferred; outbox failed.")
            }
            return pullRemoteChanges()
        }

        override suspend fun syncAccessRequestsFull(): SyncResult =
            when (val result = requestRepository.pullAccessRequests(updatedAfterIso = null)) {
                is RemoteResult.Failure -> {
                    Log.w(LogTag, "Full access_requests pull failed: ${result.reason}")
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
                            SyncResult(
                                success = true,
                                message = "Access requests synced: ${result.value.size}",
                            )
                        },
                        onFailure = { exception ->
                            Log.e(LogTag, "Full access_requests apply crashed: ${exception.message}", exception)
                            SyncResult(success = false, message = "Access requests could not be saved locally.")
                        },
                    )
                }
            }

        override suspend fun syncRequestResultsFull(): SyncResult {
            val requests = syncAccessRequestsFull()
            val grants = when (val result = requestRepository.pullExtraTimeGrants(updatedAfterIso = null)) {
                is RemoteResult.Failure -> {
                    Log.w(LogTag, "Full extra_time_grants pull failed: ${result.reason}")
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
                            SyncResult(
                                success = true,
                                message = "Extra time grants synced: ${result.value.size}",
                            )
                        },
                        onFailure = { exception ->
                            Log.e(LogTag, "Full extra_time_grants apply crashed: ${exception.message}", exception)
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

        private suspend fun pullRemoteChanges(): SyncResult {
            val results = listOf(
                pull(
                    table = SupabaseTable.Devices,
                    request = { deviceRepository.pullDevices(cursorFor(SupabaseTable.Devices)) },
                    apply = applier::applyDevices,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.Policies,
                    request = { policyRepository.pullPolicies(cursorFor(SupabaseTable.Policies)) },
                    apply = applier::applyPolicies,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.PolicyRules,
                    request = { policyRepository.pullPolicyRules(cursorFor(SupabaseTable.PolicyRules)) },
                    apply = applier::applyPolicyRules,
                    updatedAt = { it.updatedAt },
                ),
                pull(
                    table = SupabaseTable.DailyLimits,
                    request = { limitRepository.pullDailyLimits(cursorFor(SupabaseTable.DailyLimits)) },
                    apply = applier::applyDailyLimits,
                    updatedAt = { it.updatedAt },
                ),
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

        private suspend fun cursorFor(table: SupabaseTable): String? =
            syncCursorDao.cursorFor(table.tableName)?.updatedAfterIso

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
