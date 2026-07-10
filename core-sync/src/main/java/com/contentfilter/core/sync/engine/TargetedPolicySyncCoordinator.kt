package com.contentfilter.core.sync.engine

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TargetedPolicySyncCoordinator
    @Inject
    constructor(
        private val syncEngine: SyncEngine,
        private val applicationTracker: EffectivePolicyApplicationTracker,
    ) {
        private val refreshMutex = Mutex()

        suspend fun refresh(
            deviceId: String,
            policyId: String? = null,
            minimumRevision: Long? = null,
            requestId: String = UUID.randomUUID().toString(),
            reason: String,
        ): PolicyPullResult =
            refreshMutex.withLock {
                val result =
                    syncEngine.pullPolicyRevision(
                        requestId = requestId,
                        deviceId = deviceId,
                        policyId = policyId,
                        minimumRevision = minimumRevision,
                        reason = reason,
                    )
                val applied =
                    if (result.roomApplied && result.policyId != null && result.revision != null) {
                        applicationTracker.awaitApplied(
                            policyId = result.policyId,
                            revision = result.revision,
                            timeoutMillis = EffectiveApplyTimeoutMillis,
                        )
                    } else {
                        false
                    }
                if (applied) {
                    syncEngine.acknowledgePolicyApplied(
                        requestId = requestId,
                        deviceId = deviceId,
                        policyId = requireNotNull(result.policyId),
                        revision = requireNotNull(result.revision),
                    )
                }
                Log.i(
                    LogTag,
                    "policy refresh finished requestId=$requestId deviceId=${deviceId.take(8)} " +
                        "revision=${result.revision} roomApplied=${result.roomApplied} effectiveApplied=$applied reason=$reason",
                )
                result
            }

        private companion object {
            const val EffectiveApplyTimeoutMillis = 3_000L
            const val LogTag = "TargetedPolicySync"
        }
    }
