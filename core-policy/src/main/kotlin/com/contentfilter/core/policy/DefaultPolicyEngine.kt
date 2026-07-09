package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.AppPolicyContext
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyContext
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.domain.model.UpdateState

/**
 * Deterministic policy engine for app and domain decisions.
 */
class DefaultPolicyEngine : PolicyEngine {
    override fun evaluateApp(
        snapshot: PolicySnapshot,
        context: AppPolicyContext,
    ): PolicyDecision {
        deviceDecision(context.device)?.let { return it }
        activeGrant(snapshot.extraTimeGrants, PolicyTargetType.App, context.packageName, context.time)?.let {
            return PolicyDecision.GrantExtraTime(it.grantedMinutes, it.validUntilEpochMillis)
        }
        activeGrant(snapshot.extraTimeGrants, PolicyTargetType.Global, GlobalExtraTimeTarget, context.time)?.let {
            return PolicyDecision.GrantExtraTime(it.grantedMinutes, it.validUntilEpochMillis)
        }
        val appGroups =
            snapshot.appGroups.filter { group ->
                group.enabled && group.apps.any { app -> app.enabled && app.packageName == context.packageName }
            }
        appGroups.forEach { group ->
            activeGrant(snapshot.extraTimeGrants, PolicyTargetType.Category, group.id, context.time)?.let {
                return PolicyDecision.GrantExtraTime(it.grantedMinutes, it.validUntilEpochMillis)
            }
        }
        val usedMinutesToday =
            maxOf(
                context.usedMinutesToday,
                snapshot.dailyUsage.firstOrNull { it.packageName == context.packageName }?.usedMinutes ?: 0,
            )
        val limit =
            snapshot.dailyLimits
                .filter { it.enabled && it.targetType == PolicyTargetType.App && it.target == context.packageName }
                .minByOrNull { it.limitMinutes }
        if (limit != null && usedMinutesToday > limit.limitMinutes) {
            return PolicyDecision.Block("Daily limit exceeded for ${context.packageName}.")
        }
        appGroups.forEach { group ->
            val groupUsedMinutes =
                group.apps
                    .asSequence()
                    .filter { it.enabled }
                    .mapNotNull { groupApp -> snapshot.dailyUsage.firstOrNull { it.packageName == groupApp.packageName } }
                    .sumOf { it.usedMinutes }
            if (groupUsedMinutes >= group.limitMinutes) {
                return PolicyDecision.Block("Daily group limit exceeded for ${group.name}.")
            }
        }
        val rule = snapshot.rules.bestMatchingRule(context)
        return rule?.toDecision(context.packageName) ?: PolicyDecision.Allow()
    }

    override fun evaluateDomain(
        snapshot: PolicySnapshot,
        context: DomainPolicyContext,
    ): PolicyDecision {
        val normalizedContext = context.copy(domain = context.domain.normalizedDomain())
        deviceDecision(context.device)?.let { return it }
        if (normalizedContext.domain.matchesAny(CriticalAllowedDomains)) {
            return PolicyDecision.Allow()
        }
        activeGrant(
            snapshot.extraTimeGrants,
            PolicyTargetType.Global,
            GlobalExtraTimeTarget,
            normalizedContext.time,
        )?.let {
            return PolicyDecision.GrantExtraTime(it.grantedMinutes, it.validUntilEpochMillis)
        }
        val rule = snapshot.rules.bestMatchingRule(normalizedContext)
        val decision = rule?.toDecision(normalizedContext.domain) ?: PolicyDecision.Allow()
        if (decision is PolicyDecision.Allow &&
            normalizedContext.domain.isSearchProtectionDomain() &&
            snapshot.hasSearchEngineBlockRule()
        ) {
            return PolicyDecision.Block("Blocked by search protection policy.")
        }
        return decision
    }

    override fun evaluate(
        snapshot: PolicySnapshot,
        context: PolicyContext,
    ): PolicyDecision {
        val device =
            DevicePolicyContext(
                isActivated = context.healthSnapshot.licenseState != LicenseState.PendingActivation,
                healthSnapshot = context.healthSnapshot,
            )
        val time =
            TimePolicyContext(
                evaluatedAtEpochMillis = context.evaluatedAtEpochMillis,
                minuteOfDay = 0,
            )
        return when {
            context.packageName != null ->
                evaluateApp(
                    snapshot,
                    AppPolicyContext(
                        packageName = context.packageName.orEmpty(),
                        category = null,
                        usedMinutesToday = 0,
                        time = time,
                        device = device,
                    ),
                )
            context.domain != null ->
                evaluateDomain(
                    snapshot,
                    DomainPolicyContext(
                        domain = context.domain.orEmpty(),
                        category = null,
                        time = time,
                        device = device,
                    ),
                )
            else -> deviceDecision(device) ?: PolicyDecision.Allow()
        }
    }

    private fun deviceDecision(device: DevicePolicyContext): PolicyDecision? {
        val health = device.healthSnapshot
        if (!device.isActivated) {
            return PolicyDecision.RequireActivation("This device is not activated.")
        }
        if (health.updateState == UpdateState.RequiredUpdateAvailable) {
            return PolicyDecision.RequireUpdate("A required update is available.")
        }
        if (health.databaseState == ComponentState.Warning) {
            return PolicyDecision.HealthWarning("Protection is running with warnings.")
        }
        return null
    }

    private fun List<PolicyRule>.bestMatchingRule(context: AppPolicyContext): PolicyRule? =
        asSequence()
            .filter { it.enabled }
            .filter { it.isActiveAt(context.time) }
            .filter { it.matchesApp(context) }
            .sortedWith(ruleComparator)
            .firstOrNull()

    private fun List<PolicyRule>.bestMatchingRule(context: DomainPolicyContext): PolicyRule? =
        asSequence()
            .filter { it.enabled }
            .filter { it.isActiveAt(context.time) }
            .filter { it.matchesDomain(context) }
            .sortedWith(ruleComparator)
            .firstOrNull()

    private fun PolicyRule.matchesApp(context: AppPolicyContext): Boolean =
        when (scope) {
            RuleScope.App -> target == context.packageName
            RuleScope.Category -> target == context.category
            RuleScope.Global -> true
            RuleScope.Domain -> false
        }

    private fun PolicyRule.matchesDomain(context: DomainPolicyContext): Boolean =
        when (scope) {
            RuleScope.Domain -> {
                val normalizedTarget = target.normalizedDomain()
                normalizedTarget == DomainWildcard || context.domain.matchesDomainTarget(normalizedTarget)
            }
            RuleScope.Category -> target == context.category
            RuleScope.Global -> true
            RuleScope.App -> false
        }

    private fun PolicyRule.isActiveAt(time: TimePolicyContext): Boolean =
        activeWindow?.contains(time.minuteOfDay) ?: true

    private fun activeGrant(
        grants: List<ExtraTimeGrant>,
        targetType: PolicyTargetType,
        target: String,
        time: TimePolicyContext,
    ): ExtraTimeGrant? =
        grants.firstOrNull {
            it.targetType == targetType &&
                it.target == target &&
                it.validUntilEpochMillis > time.evaluatedAtEpochMillis
        }

    private fun PolicyRule.toDecision(resource: String): PolicyDecision =
        when (action) {
            RuleAction.Allow -> PolicyDecision.Allow(safeSearchRequired = safeSearchRequired)
            RuleAction.Block -> PolicyDecision.Block("Blocked by policy rule $id.")
            RuleAction.Warn -> PolicyDecision.Warn("Warning required by policy rule $id.")
            RuleAction.RequestAuthorization -> PolicyDecision.RequestAuthorization(resource)
        }

    private companion object {
        const val GlobalExtraTimeTarget = "extra_time"
        const val DomainWildcard = "*"
        val CriticalAllowedDomains =
            setOf(
                "supabase.co",
                "syeycayasyufedwoprea.supabase.co",
                "android.clients.google.com",
                "connectivitycheck.gstatic.com",
            )

        val ruleComparator: Comparator<PolicyRule> =
            compareByDescending<PolicyRule> { it.level.specificity }
                .thenByDescending { exactnessScore(it) }
                .thenByDescending { it.priority }

        fun exactnessScore(rule: PolicyRule): Int =
            when (rule.scope) {
                RuleScope.App,
                RuleScope.Domain,
                -> 2
                RuleScope.Category -> 1
                RuleScope.Global -> 0
            }

        fun String.normalizedDomain(): String =
            trim()
                .lowercase()
                .removeSuffix(".")
                .removePrefix("www.")

        fun String.matchesDomainTarget(target: String): Boolean = this == target || endsWith(".$target")

        fun String.matchesAny(targets: Set<String>): Boolean = targets.any { matchesDomainTarget(it) }

        fun PolicySnapshot.hasSearchEngineBlockRule(): Boolean =
            rules.any {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.action == RuleAction.Block &&
                    it.target.normalizedDomain().isSearchProtectionRuleTarget()
            }

        fun String.isSearchProtectionRuleTarget(): Boolean =
            SearchEngineCatalog.searchEngineDomains.any { matchesDomainTarget(it) || it.matchesDomainTarget(this) } ||
                SearchEngineCatalog.secureDnsDomains.any { matchesDomainTarget(it) || it.matchesDomainTarget(this) }

        fun String.isSearchProtectionDomain(): Boolean =
            SearchEngineCatalog.searchEngineDomains.any { matchesDomainTarget(it) } ||
                SearchEngineCatalog.secureDnsDomains.any { matchesDomainTarget(it) }
    }
}
