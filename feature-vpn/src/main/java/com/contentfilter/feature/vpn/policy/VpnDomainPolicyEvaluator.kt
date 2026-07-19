package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isAllowedWindow
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.onlySearchResultsEnabled
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.policy.PolicyEngine
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import javax.inject.Inject

/**
 * Converts VPN DNS observations into domain policy contexts.
 */
class VpnDomainPolicyEvaluator
    @Inject
    constructor(
        private val policyEngine: PolicyEngine,
        private val clock: VpnClock,
        private val domainBlocklist: DynamicDomainBlocklist,
    ) {
        private val dnsUsageTracker = DomainDnsUsageTracker()

        fun evaluate(
            domain: String,
            snapshot: PolicySnapshot,
            health: SystemHealthSnapshot,
        ): PolicyDecision {
            val now = clock.nowEpochMillis()
            val minuteOfDay = clock.minuteOfDay(now)
            val isoDayOfWeek = clock.isoDayOfWeek(now)
            val normalizedDomain = domain.normalizedDomain()
            val policyDecision =
                policyEngine.evaluateDomain(
                    snapshot = snapshot,
                    context =
                        DomainPolicyContext(
                            domain = normalizedDomain,
                            category = null,
                            time =
                                TimePolicyContext(
                                    evaluatedAtEpochMillis = now,
                                    minuteOfDay = minuteOfDay,
                                    isoDayOfWeek = isoDayOfWeek,
                                ),
                            device =
                                DevicePolicyContext(
                                    isActivated = health.licenseState.isActivated(),
                                    healthSnapshot = health,
                                ),
                        ),
                )
            if (policyDecision is PolicyDecision.RequireActivation) return policyDecision
            val webBlocked = snapshot.rules.webNavigationBlocked()
            val onlyResults = !webBlocked && snapshot.rules.onlySearchResultsEnabled()
            if (webBlocked || (onlyResults && policyDecision !is PolicyDecision.Allow)) {
                return policyDecision
            }
            val technicalHostAllowed =
                policyDecision is PolicyDecision.Allow && normalizedDomain.isTechnicalWebProtectionHost()
            val explicitDecision =
                snapshot.rules
                    .bestExplicitDomainRule(
                        normalizedDomain,
                        TimePolicyContext(now, minuteOfDay, isoDayOfWeek),
                    )?.toDecision(normalizedDomain)
            if (explicitDecision != null && explicitDecision !is PolicyDecision.Allow) return explicitDecision
            if (policyDecision !is PolicyDecision.Allow) return policyDecision
            val finalDecision = explicitDecision ?: policyDecision
            if (explicitDecision == null && !technicalHostAllowed) {
                domainBlocklist.categoryFor(normalizedDomain)?.let { category ->
                    return PolicyDecision.Block("Blocked by local domain category: $category.")
                }
            }
            val limit =
                snapshot.dailyLimits
                    .filter {
                        it.enabled &&
                            it.targetType == PolicyTargetType.Domain &&
                            normalizedDomain.matchesLimitTarget(it.target.normalizedDomain())
                    }
                    .minByOrNull { it.limitMinutes }
            if (limit != null) {
                val observedMinutes = dnsUsageTracker.recordMinute(limit.target, now, minuteOfDay)
                if (observedMinutes > limit.limitMinutes) {
                    return PolicyDecision.Block("Daily DNS event limit exceeded for ${limit.target}.")
                }
            }
            return finalDecision
        }

        private fun List<PolicyRule>.bestExplicitDomainRule(
            domain: String,
            time: TimePolicyContext,
        ): PolicyRule? =
            asSequence()
                .filter { it.enabled && it.scope == RuleScope.Domain }
                .filterNot { it.isAllowedWindow() }
                .filterNot { it.id.startsWith("safe-default-") }
                .filter { !it.target.startsWith("__") && it.target != "*" }
                .filter { it.activeWindow?.contains(time, it.activeDaysMask) != false }
                .filter { domain.matchesLimitTarget(it.target.normalizedDomain()) }
                .sortedWith(
                    compareByDescending<PolicyRule> { it.level.specificity }
                        .thenByDescending { it.priority },
                )
                .firstOrNull()

        private fun PolicyRule.toDecision(domain: String): PolicyDecision =
            when (action) {
                RuleAction.Allow ->
                    PolicyDecision.Allow(
                        safeSearchRequired = safeSearchRequired || WebNavigationPolicy.isSearchEngineDomain(domain),
                    )
                RuleAction.Block -> PolicyDecision.Block("Blocked by explicit domain policy.")
                RuleAction.Warn -> PolicyDecision.Warn("Warning required by explicit domain policy.")
                RuleAction.RequestAuthorization -> PolicyDecision.RequestAuthorization(domain)
            }

        private fun String.isTechnicalWebProtectionHost(): Boolean =
            SearchEngineCatalog.isSearchResultsAllowedDomain(this) ||
                TechnicalAllowedDomains.any { matchesLimitTarget(it) }

        private fun String.matchesLimitTarget(target: String): Boolean = this == target || endsWith(".$target")

        private fun String.normalizedDomain(): String =
            trim()
                .lowercase()
                .removeSuffix(".")
                .removePrefix("www.")

        private companion object {
            val TechnicalAllowedDomains =
                setOf(
                    "supabase.co",
                    "android.clients.google.com",
                    "connectivitycheck.gstatic.com",
                )
        }
    }
