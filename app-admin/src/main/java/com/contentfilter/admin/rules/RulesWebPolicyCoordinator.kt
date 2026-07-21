package com.contentfilter.admin.rules

import android.util.Log
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.usecase.admin.SavePolicyRuleUseCase
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.PolicyApplicationResult
import com.contentfilter.core.sync.engine.PolicyFastSyncResult
import com.contentfilter.core.sync.engine.SyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private data class WebPreferenceSaveKey(
    val deviceId: String,
    val preference: WebPolicyPreference,
)

internal class WebPreferenceRequestTracker {
    private var requestSequence = 0L
    private var latestMessageRequestId = 0L
    private val latestRequestIds = mutableMapOf<WebPreferenceSaveKey, Long>()

    @Synchronized
    fun begin(
        preference: WebPolicyPreference,
        deviceId: String,
    ): Long {
        val requestId = ++requestSequence
        latestRequestIds[WebPreferenceSaveKey(deviceId, preference)] = requestId
        latestMessageRequestId = requestId
        return requestId
    }

    @Synchronized
    fun isCurrent(
        deviceId: String,
        preference: WebPolicyPreference,
        requestId: Long,
    ): Boolean = latestRequestIds[WebPreferenceSaveKey(deviceId, preference)] == requestId

    @Synchronized
    fun isLatestMessage(
        deviceId: String,
        selectedDeviceId: String?,
        requestId: Long,
    ): Boolean = latestMessageRequestId == requestId && selectedDeviceId == deviceId
}

internal class RulesWebPolicyCoordinator
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val saveRule: SavePolicyRuleUseCase,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) {
        private val requestTracker = WebPreferenceRequestTracker()
        private val mutationMutex = Mutex()

        fun begin(
            preference: WebPolicyPreference,
            deviceId: String,
        ): Long = requestTracker.begin(preference, deviceId)

        fun isCurrent(
            deviceId: String,
            preference: WebPolicyPreference,
            requestId: Long,
        ): Boolean = requestTracker.isCurrent(deviceId, preference, requestId)

        fun isLatestMessage(
            deviceId: String,
            selectedDeviceId: String?,
            requestId: Long,
        ): Boolean = requestTracker.isLatestMessage(deviceId, selectedDeviceId, requestId)

        suspend fun saveLocal(
            preference: WebPolicyPreference,
            requestedState: Boolean,
            deviceId: String,
            traceRequestId: String,
        ): Result<PolicyMutationReceipt> =
            mutationMutex.withLock {
                runCatching {
                    syncEngine.pullPolicyRevision(
                        requestId = traceRequestId,
                        deviceId = deviceId,
                        reason = "admin-web-preflight",
                    )
                    val before = policyRepository.getActivePolicy(deviceId)
                    check(before.deviceId == null || before.deviceId == deviceId) {
                        "Room devolvió una policy de otro dispositivo."
                    }
                    val desired = before.rules.webPolicyPreferences().withPreference(preference, requestedState)
                    val changes =
                        before.rules.webPolicyPreferenceChanges(
                            preference = preference,
                            enabled = requestedState,
                            deviceId = deviceId,
                        )
                    val receipt = saveRule.saveAll(changes, deviceId, traceRequestId)
                    val applied =
                        withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                            policyRepository.observeActivePolicy(deviceId).first { snapshot ->
                                snapshot.deviceId == deviceId &&
                                    snapshot.version >= receipt.revision &&
                                    snapshot.rules.webPolicyPreferences() == desired &&
                                    snapshot.rules.activeWebAuxiliaryBlockCount() == 0
                            }
                        } ?: policyRepository.getActivePolicy(deviceId).takeIf { snapshot ->
                            snapshot.deviceId == deviceId &&
                                snapshot.rules.webPolicyPreferences().matchesPreference(preference, requestedState) &&
                                snapshot.rules.activeWebAuxiliaryBlockCount() == 0
                        }
                    if (applied == null) error("Room no confirmó la preferencia Web.")
                    if (BuildConfig.FLAVOR == "dev") {
                        Log.i(
                            LogTag,
                            "web option room confirmed requestId=$traceRequestId " +
                                "deviceId=${deviceId.safeDeviceId()} policyId=${receipt.policyId.take(8)} " +
                                "revision=${receipt.revision} changed=${preference.name} changes=${changes.size}",
                        )
                    }
                    receipt
                }
            }

        suspend fun isApplied(
            deviceId: String,
            preference: WebPolicyPreference,
            requestedState: Boolean,
        ): Boolean =
            runCatching {
                policyRepository
                    .getActivePolicy(deviceId)
                    .rules
                    .webPolicyPreferences()
                    .matchesPreference(preference, requestedState)
            }.getOrDefault(false)

        fun requestSync() = syncScheduler.requestSync()

        suspend fun sync(receipt: PolicyMutationReceipt): PolicyFastSyncResult = syncEngine.syncPolicyChanges(receipt)

        suspend fun waitUntilApplied(receipt: PolicyMutationReceipt): PolicyApplicationResult =
            syncEngine.waitForPolicyApplied(receipt, PolicyApplicationWaitMillis)

        private companion object {
            const val PolicyApplicationWaitMillis = 8_000L
        }
    }
