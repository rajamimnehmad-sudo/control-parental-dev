package com.contentfilter.feature.accessibility.policy

import com.contentfilter.core.domain.model.AppPolicyContext
import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.policy.DefaultPolicyEngine
import javax.inject.Inject

class AccessibilityAppPolicyEvaluator
    @Inject
    constructor(
        private val clock: AccessibilityClock,
    ) {
        private val policyEngine = DefaultPolicyEngine()

        fun evaluate(
            packageName: String,
            usedMinutesToday: Int,
            snapshot: PolicySnapshot,
            health: SystemHealthSnapshot,
        ): PolicyDecision =
            policyEngine.evaluateApp(
                snapshot = snapshot,
                context = buildContext(packageName, usedMinutesToday, health),
            )

        fun buildContext(
            packageName: String,
            usedMinutesToday: Int,
            health: SystemHealthSnapshot,
        ): AppPolicyContext {
            val now = clock.nowEpochMillis()
            return AppPolicyContext(
                packageName = packageName,
                category = null,
                usedMinutesToday = usedMinutesToday,
                time = TimePolicyContext(
                    evaluatedAtEpochMillis = now,
                    minuteOfDay = clock.minuteOfDay(now),
                ),
                device = DevicePolicyContext(
                    isActivated = health.licenseState != LicenseState.PendingActivation,
                    healthSnapshot = health,
                ),
            )
        }
    }
