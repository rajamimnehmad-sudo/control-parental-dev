package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
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
        fun evaluate(
            domain: String,
            snapshot: PolicySnapshot,
            health: SystemHealthSnapshot,
        ): PolicyDecision {
            val now = clock.nowEpochMillis()
            return policyEngine.evaluateDomain(
                snapshot = snapshot,
                context = DomainPolicyContext(
                    domain = domain.lowercase(),
                    category = null,
                    time = TimePolicyContext(
                        evaluatedAtEpochMillis = now,
                        minuteOfDay = clock.minuteOfDay(now),
                    ),
                    device = DevicePolicyContext(
                        isActivated = health.licenseState.isActivated(),
                        healthSnapshot = health,
                    ),
                ),
            )
        }
    }
