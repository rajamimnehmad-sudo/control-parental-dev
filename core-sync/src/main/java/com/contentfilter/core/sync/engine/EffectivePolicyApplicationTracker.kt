package com.contentfilter.core.sync.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class PolicyConsumer {
    Vpn,
    Accessibility,
}

@Singleton
class EffectivePolicyApplicationTracker
    @Inject
    constructor() {
        private val applied = MutableStateFlow<Map<PolicyConsumer, AppliedPolicyRevision>>(emptyMap())

        fun report(
            consumer: PolicyConsumer,
            policyId: String,
            revision: Long,
        ) {
            applied.update { current ->
                val previous = current[consumer]
                if (previous != null && previous.revision > revision) {
                    current
                } else {
                    current + (consumer to AppliedPolicyRevision(policyId, revision, System.currentTimeMillis()))
                }
            }
            Log.i(
                LogTag,
                "effective policy consumer=${consumer.name} policyId=${policyId.take(8)} revision=$revision",
            )
        }

        suspend fun awaitApplied(
            policyId: String,
            revision: Long,
            timeoutMillis: Long,
        ): Boolean =
            withTimeoutOrNull(timeoutMillis) {
                applied.first { current ->
                    RequiredConsumers.all { consumer ->
                        current[consumer]?.let { it.policyId == policyId && it.revision >= revision } == true
                    }
                }
            } != null

        internal fun isApplied(
            policyId: String,
            revision: Long,
        ): Boolean =
            RequiredConsumers.all { consumer ->
                applied.value[consumer]?.let { it.policyId == policyId && it.revision >= revision } == true
            }

        private companion object {
            val RequiredConsumers = setOf(PolicyConsumer.Vpn, PolicyConsumer.Accessibility)
            const val LogTag = "PolicyApplication"
        }
    }

private data class AppliedPolicyRevision(
    val policyId: String,
    val revision: Long,
    val appliedAtEpochMillis: Long,
)
