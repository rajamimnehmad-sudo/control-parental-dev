package com.contentfilter.core.sync.engine

import android.util.Log
import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.entity.SyncCursorEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import com.contentfilter.core.network.remote.RemoteAccountRepository
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.RemoteLicenseRepository
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseTable
import com.contentfilter.core.sync.outbox.OutboxProcessor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

class DefaultSyncEngine
    @Inject
    constructor(
        private val outboxProcessor: OutboxProcessor,
        private val accountRepository: RemoteAccountRepository,
        private val deviceRepository: RemoteDeviceRepository,
        private val policyRepository: RemotePolicyRepository,
        private val limitRepository: RemoteLimitRepository,
        private val licenseRepository: RemoteLicenseRepository,
        private val requestRepository: RemoteRequestRepository,
        private val syncCursorDao: SyncCursorDao,
        private val applier: RemoteApplier,
        private val systemStatusRepository: SystemStatusRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
    ) : SyncEngine {
        private val policySyncMutex = Mutex()
        private val targetedPolicyPullMutex = Mutex()

        override suspend fun syncOnce(): SyncResult = policySyncMutex.withLock { syncOnceLocked() }

        private suspend fun syncOnceLocked(): SyncResult {
            refreshLicenseEntitlement()
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

        override suspend fun syncCoreDataFull(): SyncResult = policySyncMutex.withLock { syncCoreDataFullLocked() }

        private suspend fun syncCoreDataFullLocked(): SyncResult {
            refreshLicenseEntitlement()
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
                refreshLicenseEntitlement()
                markCurrentDeviceSeen()
                syncDevicesFullInternal()
            }

        private suspend fun refreshLicenseEntitlement() {
            systemStatusRepository.refreshLicenseState()
            val activation = deviceActivationRepository.currentActivation() ?: return
            when (val result = licenseRepository.getDeviceEntitlement(activation.deviceId)) {
                is RemoteResult.Success -> systemStatusRepository.updateLicenseEntitlement(result.value)
                is RemoteResult.Failure -> Log.w(LogTag, "License refresh deferred: ${result.reason}")
            }
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

        override suspend fun syncPolicyChanges(receipt: PolicyMutationReceipt): PolicyFastSyncResult {
            val startedAt = System.currentTimeMillis()
            val result = outboxProcessor.processPolicyMutation(receipt)
            Log.i(
                LogTag,
                "policy fast push requestId=${receipt.requestId} deviceId=${receipt.deviceId.take(8)} " +
                    "policyId=${receipt.policyId.take(8)} revision=${result.revision} " +
                    "serverConfirmed=${result.serverConfirmed} pending=${result.pendingOperationIds.size} " +
                    "durationMs=${System.currentTimeMillis() - startedAt}",
            )
            return PolicyFastSyncResult(
                localSaved = true,
                serverConfirmed = result.serverConfirmed,
                notificationDelivered = result.notificationDelivered,
                policyId = receipt.policyId,
                revision = result.revision,
                pendingOperationIds = result.pendingOperationIds,
                error = result.error,
            )
        }

        override suspend fun pullPolicyRevision(
            requestId: String,
            deviceId: String,
            policyId: String?,
            minimumRevision: Long?,
            reason: String,
        ): PolicyPullResult =
            targetedPolicyPullMutex.withLock {
                val startedAt = System.currentTimeMillis()
                Log.i(
                    LogTag,
                    "policy fast pull start requestId=$requestId deviceId=${deviceId.take(8)} " +
                        "policyId=${policyId?.take(8) ?: "active"} minimumRevision=$minimumRevision reason=$reason",
                )
                val policiesResult =
                    if (policyId == null) {
                        policyRepository.pullPoliciesForDevice(deviceId)
                    } else {
                        policyRepository.pullPolicyById(policyId)
                    }
                val policies =
                    when (policiesResult) {
                        is RemoteResult.Success -> policiesResult.value
                        is RemoteResult.Failure ->
                            return@withLock failedPolicyPull(
                                requestId = requestId,
                                deviceId = deviceId,
                                policyId = policyId,
                                reason = policiesResult.reason,
                                startedAt = startedAt,
                            )
                    }
                val policy =
                    policies
                        .asSequence()
                        .filter { it.deviceId == deviceId && it.active && it.deletedAt == null }
                        .maxWithOrNull(compareBy({ it.version }, { it.updatedAt }))
                        ?: return@withLock failedPolicyPull(
                            requestId = requestId,
                            deviceId = deviceId,
                            policyId = policyId,
                            reason = "No active policy revision is available yet.",
                            startedAt = startedAt,
                        )
                if (minimumRevision != null && policy.version < minimumRevision) {
                    return@withLock failedPolicyPull(
                        requestId = requestId,
                        deviceId = deviceId,
                        policyId = policy.id,
                        reason = "Requested policy revision is not available yet.",
                        startedAt = startedAt,
                    )
                }
                val bundle =
                    coroutineScope {
                        val rules = async { policyRepository.pullPolicyRulesForPolicy(policy.id) }
                        val limits = async { limitRepository.pullDailyLimitsForPolicy(policy.id) }
                        val groups = async { limitRepository.pullAppGroupsForDevice(deviceId) }
                        val groupApps = async { limitRepository.pullAppGroupAppsForDevice(deviceId) }
                        PolicyRemoteBundle(
                            rules = rules.await(),
                            limits = limits.await(),
                            groups = groups.await(),
                            groupApps = groupApps.await(),
                        )
                    }
                val bundleFailure = bundle.failureReason()
                if (bundleFailure != null) {
                    return@withLock failedPolicyPull(
                        requestId = requestId,
                        deviceId = deviceId,
                        policyId = policy.id,
                        reason = bundleFailure,
                        startedAt = startedAt,
                    )
                }
                val applied =
                    applier.applyPolicyBundle(
                        policy = policy,
                        rules = (bundle.rules as RemoteResult.Success).value,
                        limits = (bundle.limits as RemoteResult.Success).value,
                        groups = (bundle.groups as RemoteResult.Success).value,
                        groupApps = (bundle.groupApps as RemoteResult.Success).value,
                    )
                Log.i(
                    LogTag,
                    "policy fast pull applied requestId=$requestId deviceId=${deviceId.take(8)} " +
                        "policyId=${policy.id.take(8)} revision=${policy.version} roomApplied=$applied " +
                        "rules=${(bundle.rules as RemoteResult.Success).value.size} " +
                        "limits=${(bundle.limits as RemoteResult.Success).value.size} " +
                        "durationMs=${System.currentTimeMillis() - startedAt}",
                )
                PolicyPullResult(
                    success = applied,
                    requestId = requestId,
                    deviceId = deviceId,
                    policyId = policy.id,
                    revision = policy.version,
                    roomApplied = applied,
                    error = if (applied) null else "A newer local revision was already active.",
                )
            }

        override suspend fun acknowledgePolicyApplied(
            requestId: String,
            deviceId: String,
            policyId: String,
            revision: Long,
        ): Boolean {
            val startedAt = System.currentTimeMillis()
            val result = deviceRepository.acknowledgePolicyApplied(deviceId, policyId, revision)
            val success = result is RemoteResult.Success
            Log.i(
                LogTag,
                "policy applied ack requestId=$requestId deviceId=${deviceId.take(8)} " +
                    "policyId=${policyId.take(8)} revision=$revision success=$success " +
                    "durationMs=${System.currentTimeMillis() - startedAt}",
            )
            return success
        }

        override suspend fun waitForPolicyApplied(
            receipt: PolicyMutationReceipt,
            timeoutMillis: Long,
        ): PolicyApplicationResult {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var remoteAvailable = false
            do {
                when (val result = deviceRepository.pullDevice(receipt.deviceId)) {
                    is RemoteResult.Success -> {
                        remoteAvailable = true
                        val device = result.value.firstOrNull { it.id == receipt.deviceId }
                        if (
                            device?.appliedPolicyId == receipt.policyId &&
                            (device.appliedPolicyRevision ?: 0L) >= receipt.revision
                        ) {
                            return PolicyApplicationResult(
                                state = PolicyApplicationState.Applied,
                                revision = device.appliedPolicyRevision ?: receipt.revision,
                                appliedAtEpochMillis = device.policyAppliedAt?.let { Instant.parse(it).toEpochMilli() },
                            )
                        }
                    }
                    is RemoteResult.Failure -> Unit
                }
                if (System.currentTimeMillis() < deadline) delay(PolicyAckPollMillis)
            } while (System.currentTimeMillis() < deadline)
            return PolicyApplicationResult(
                state = if (remoteAvailable) PolicyApplicationState.Pending else PolicyApplicationState.Offline,
                revision = receipt.revision,
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

        private fun failedPolicyPull(
            requestId: String,
            deviceId: String,
            policyId: String?,
            reason: String,
            startedAt: Long,
        ): PolicyPullResult {
            Log.w(
                LogTag,
                "policy fast pull failed requestId=$requestId deviceId=${deviceId.take(8)} " +
                    "policyId=${policyId?.take(8) ?: "active"} durationMs=${System.currentTimeMillis() - startedAt} " +
                    "reason=$reason",
            )
            return PolicyPullResult(
                success = false,
                requestId = requestId,
                deviceId = deviceId,
                policyId = policyId,
                revision = null,
                roomApplied = false,
                error = reason,
            )
        }

        private companion object {
            const val LogTag = "SyncEngine"
            const val PolicyAckPollMillis = 500L
        }
    }

private data class PolicyRemoteBundle(
    val rules: RemoteResult<List<RemotePolicyRuleDto>>,
    val limits: RemoteResult<List<RemoteDailyLimitDto>>,
    val groups: RemoteResult<List<RemoteAppGroupDto>>,
    val groupApps: RemoteResult<List<RemoteAppGroupAppDto>>,
) {
    fun failureReason(): String? =
        listOf(rules, limits, groups, groupApps)
            .filterIsInstance<RemoteResult.Failure>()
            .firstOrNull()
            ?.reason
}
