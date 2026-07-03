package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.policy.PolicyEngine
import javax.inject.Inject

/**
 * Converts VPN DNS observations into domain policy contexts.
 */
class VpnDomainPolicyEvaluator
    @Inject
    constructor(
        private val policyEngine: PolicyEngine,
        private val clock: VpnClock,
    ) {
        private val dnsUsageTracker = DomainDnsUsageTracker()

        fun evaluate(
            domain: String,
            snapshot: PolicySnapshot,
            health: SystemHealthSnapshot,
        ): PolicyDecision {
            val now = clock.nowEpochMillis()
            val minuteOfDay = clock.minuteOfDay(now)
            val normalizedDomain = domain.normalizedDomain()
            val policyDecision = policyEngine.evaluateDomain(
                snapshot = snapshot,
                context = DomainPolicyContext(
                    domain = normalizedDomain,
                    category = null,
                    time = TimePolicyContext(
                        evaluatedAtEpochMillis = now,
                        minuteOfDay = minuteOfDay,
                    ),
                    device = DevicePolicyContext(
                        isActivated = health.licenseState.isActivated(),
                        healthSnapshot = health,
                    ),
                ),
            )
            val limit = snapshot.dailyLimits
                .filter {
                    it.enabled &&
                        it.targetType == PolicyTargetType.Domain &&
                        normalizedDomain.matchesLimitTarget(it.target.normalizedDomain())
                }
                .minByOrNull { it.limitMinutes }
            if (limit != null && policyDecision is PolicyDecision.Allow) {
                val observedMinutes = dnsUsageTracker.recordMinute(limit.target, now, minuteOfDay)
                if (observedMinutes > limit.limitMinutes) {
                    return PolicyDecision.Block("Daily DNS event limit exceeded for ${limit.target}.")
                }
            }
            return policyDecision
        }

        private fun String.matchesLimitTarget(target: String): Boolean =
            this == target || endsWith(".$target")

        private fun String.normalizedDomain(): String =
            trim()
                .lowercase()
                .removeSuffix(".")
                .removePrefix("www.")
    }
