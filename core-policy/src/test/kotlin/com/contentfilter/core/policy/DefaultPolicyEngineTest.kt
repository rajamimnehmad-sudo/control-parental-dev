package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.AppPolicyContext
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.DevicePolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.WebNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPolicyEngineTest {
    private val engine = DefaultPolicyEngine()

    @Test
    fun `specific rule wins over general rule`() {
        val decision =
            engine.evaluateApp(
                snapshot =
                    policy(
                        rules =
                            listOf(
                                rule(
                                    id = "global-block",
                                    level = PolicyLevel.Global,
                                    scope = RuleScope.Global,
                                    action = RuleAction.Block,
                                ),
                                rule(
                                    id = "device-allow",
                                    level = PolicyLevel.Device,
                                    target = BlockedPackage,
                                    action = RuleAction.Allow,
                                ),
                            ),
                    ),
                context = appContext(packageName = BlockedPackage),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `approved app exception wins over account block`() {
        val decision =
            engine.evaluateApp(
                snapshot =
                    policy(
                        rules =
                            listOf(
                                rule(
                                    id = "account-block",
                                    target = BlockedPackage,
                                    action = RuleAction.Block,
                                    priority = 0,
                                ),
                                rule(
                                    id = "approved-allow",
                                    target = BlockedPackage,
                                    action = RuleAction.Allow,
                                    priority = 1_000,
                                ),
                            ),
                    ),
                context = appContext(packageName = BlockedPackage),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `blocks app by package rule`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(rules = listOf(rule(target = BlockedPackage, action = RuleAction.Block))),
                context = appContext(packageName = BlockedPackage),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `allows app while daily limit is still available`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(limits = listOf(limit(target = AllowedPackage, minutes = 60))),
                context = appContext(packageName = AllowedPackage, usedMinutesToday = 30),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `blocks app when daily limit is exceeded`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(limits = listOf(limit(target = BlockedPackage, minutes = 60))),
                context = appContext(packageName = BlockedPackage, usedMinutesToday = 61),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `allows app when daily limit is exactly reached`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(limits = listOf(limit(target = AllowedPackage, minutes = 60))),
                context = appContext(packageName = AllowedPackage, usedMinutesToday = 60),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `allows exact domain`() {
        val decision =
            engine.evaluateDomain(
                snapshot = policy(rules = listOf(domainRule(target = "example.com", action = RuleAction.Allow))),
                context = domainContext(domain = "example.com"),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `allows subdomain`() {
        val decision =
            engine.evaluateDomain(
                snapshot = policy(rules = listOf(domainRule(target = "example.com", action = RuleAction.Allow))),
                context = domainContext(domain = "kids.example.com"),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `blocks domain`() {
        val decision =
            engine.evaluateDomain(
                snapshot = policy(rules = listOf(domainRule(target = "blocked.test", action = RuleAction.Block))),
                context = domainContext(domain = "blocked.test"),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `stale google web rule does not block when web navigation is open`() {
        val snapshot = policy(rules = listOf(domainRule(target = "google.com", action = RuleAction.Block)))

        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("www.google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("search.google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("clients4.google.com")))
    }

    @Test
    fun `stale google argentina web rule does not block when web navigation is open`() {
        val snapshot = policy(rules = listOf(domainRule(target = "google.com.ar", action = RuleAction.Block)))

        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com.ar")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("www.google.com.ar")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("search.google.com.ar")))
    }

    @Test
    fun `search protection rule does not block support domains when web navigation is open`() {
        val snapshot = policy(rules = listOf(domainRule(target = "bing.com", action = RuleAction.Block)))

        val decision = engine.evaluateDomain(snapshot, domainContext("clients4.google.com"))

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `web navigation block protects clientservices without blocking all googleapis`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                        domainRule(target = "google.com", action = RuleAction.Block),
                    ),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("clientservices.googleapis.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("maps.googleapis.com")))
    }

    @Test
    fun `blocks search engines when web is blocked and search engines are disabled`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                        domainRule(target = "*", action = RuleAction.Block),
                    ),
            )

        SearchEngineCatalog.searchEngineDomains.forEach { domain ->
            val decision = engine.evaluateDomain(snapshot, domainContext(domain))

            assertIs<PolicyDecision.Block>(decision, "Expected $domain to be blocked")
        }
    }

    @Test
    fun `web navigation block blocks search domains without blocking app domains`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                    ),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("duckduckgo.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("api.whatsapp.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("maps.googleapis.com")))
    }

    @Test
    fun `blocks search engines and result domains when web is blocked`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                        domainRule(target = "*", action = RuleAction.Block),
                    ),
            )

        SearchEngineCatalog.searchEngineDomains.forEach { domain ->
            val decision = engine.evaluateDomain(snapshot, domainContext(domain))

            assertIs<PolicyDecision.Block>(decision, "Expected $domain to be blocked")
        }
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("www.google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("www.google.com.ar")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("www.bing.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("duckduckgo.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("app.com")))
    }

    @Test
    fun `blocks search engine when explicit search block overrides stale allow`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                        domainRule(target = "*", action = RuleAction.Block, priority = 10),
                        domainRule(target = "google.com", action = RuleAction.Allow, priority = 1_000),
                        domainRule(target = "google.com", action = RuleAction.Block, priority = 3_000),
                    ),
            )

        val decision = engine.evaluateDomain(snapshot, domainContext("www.google.com"))

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `blocks secure dns providers when explicit search protection block exists`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            priority = WebNavigationPolicy.RulePriority,
                        ),
                        domainRule(target = "*", action = RuleAction.Block, priority = 10),
                        domainRule(target = "dns.google", action = RuleAction.Allow, priority = 1_000),
                        domainRule(target = "dns.google", action = RuleAction.Block, priority = 3_000),
                    ),
            )

        val decision = engine.evaluateDomain(snapshot, domainContext("dns.google"))

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `safe default snapshot does not block without web navigation rule`() {
        val snapshot = SearchProtectionPolicyDefaults.safeDefaultSnapshot()

        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("clients4.google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("dns.google")))
    }

    @Test
    fun `restricted search mode allows all catalog engines and marks SafeSearch`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                        domainRule(target = WebNavigationPolicy.SafeSearchTarget, action = RuleAction.Allow),
                    ),
            )

        listOf("google.com", "bing.com", "search.yahoo.com", "duckduckgo.com").forEach { domain ->
            val decision = assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext(domain)))
            assertTrue(decision.safeSearchRequired)
        }
    }

    @Test
    fun `SafeSearch enforcement is identical for normal and private browser sessions`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(target = WebNavigationPolicy.SafeSearchTarget, action = RuleAction.Allow),
                    ),
            )

        repeat(2) {
            val decision = assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
            assertTrue(decision.safeSearchRequired)
        }
    }

    @Test
    fun `SafeSearch and image filtering block known encrypted DNS bootstrap hosts`() {
        val safeSearch =
            policy(
                rules =
                    listOf(
                        domainRule(target = WebNavigationPolicy.SafeSearchTarget, action = RuleAction.Allow),
                    ),
            )
        val images =
            policy(
                rules =
                    listOf(
                        domainRule(target = WebNavigationPolicy.ImagesBlockedTarget, action = RuleAction.Block),
                    ),
            )

        listOf("dns.google", "chrome.cloudflare-dns.com", "dns9.quad9.net").forEach { host ->
            assertIs<PolicyDecision.Block>(engine.evaluateDomain(safeSearch, domainContext(host)))
            assertIs<PolicyDecision.Block>(engine.evaluateDomain(images, domainContext(host)))
        }
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(safeSearch, domainContext("example.com")))
    }

    @Test
    fun `restricted search mode blocks top level external navigation`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                    ),
            )

        val decision =
            engine.evaluateDomain(
                snapshot,
                domainContext("example.com", sourceDomain = "google.com", topLevelNavigation = true),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `released search mode allows top level external navigation`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ),
                    ),
            )

        val decision =
            engine.evaluateDomain(
                snapshot,
                domainContext("example.com", sourceDomain = "duckduckgo.com", topLevelNavigation = true),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `SafeSearch remains independent from external result navigation`() {
        val safeSearchOff =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.SafeSearchTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                    ),
            )
        val decision = assertIs<PolicyDecision.Allow>(engine.evaluateDomain(safeSearchOff, domainContext("bing.com")))

        assertFalse(decision.safeSearchRequired)
        assertIs<PolicyDecision.Block>(
            engine.evaluateDomain(
                safeSearchOff,
                domainContext("example.com", sourceDomain = "bing.com", topLevelNavigation = true),
            ),
        )
    }

    @Test
    fun `image preference blocks known image hosts without blocking search pages`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ImagesBlockedTarget,
                            action = RuleAction.Block,
                        ),
                    ),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("encrypted-tbn8.gstatic.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("tse2.mm.bing.net")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("images.google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
    }

    @Test
    fun `global web block wins over released results and SafeSearch preferences`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(target = WebNavigationPolicy.RuleTarget, action = RuleAction.Block),
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ),
                        domainRule(target = WebNavigationPolicy.SafeSearchTarget, action = RuleAction.Allow),
                    ),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("google.com")))
    }

    @Test
    fun `newer allowed snapshot releases a previously blocked browser domain`() {
        val mainRule =
            domainRule(
                target = WebNavigationPolicy.RuleTarget,
                action = RuleAction.Block,
                priority = WebNavigationPolicy.RulePriority,
            )
        val blocked =
            policy(
                rules = listOf(mainRule, domainRule(target = "google.com", action = RuleAction.Block)),
            )
        val allowed =
            blocked.copy(
                version = blocked.version + 1,
                rules =
                    blocked.rules.map { rule ->
                        if (rule.target == WebNavigationPolicy.RuleTarget) rule.copy(enabled = false) else rule
                    },
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(blocked, domainContext("google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(allowed, domainContext("google.com")))
    }

    @Test
    fun `approved domain exception wins over account block`() {
        val decision =
            engine.evaluateDomain(
                snapshot =
                    policy(
                        rules =
                            listOf(
                                domainRule(
                                    id = "domain-block",
                                    target = "blocked.test",
                                    action = RuleAction.Block,
                                    priority = 0,
                                ),
                                domainRule(
                                    id = "approved-domain",
                                    target = "blocked.test",
                                    action = RuleAction.Allow,
                                    priority = 1_000,
                                ),
                            ),
                    ),
                context = domainContext(domain = "blocked.test"),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `uses active temporary approval`() {
        val decision =
            engine.evaluateApp(
                snapshot =
                    policy(
                        limits = listOf(limit(target = BlockedPackage, minutes = 1)),
                        grants = listOf(grant(target = BlockedPackage, validUntil = Now + 1_000)),
                    ),
                context = appContext(packageName = BlockedPackage, usedMinutesToday = 100),
            )

        assertIs<PolicyDecision.GrantExtraTime>(decision)
    }

    @Test
    fun `ignores expired temporary approval`() {
        val decision =
            engine.evaluateApp(
                snapshot =
                    policy(
                        limits = listOf(limit(target = BlockedPackage, minutes = 1)),
                        grants = listOf(grant(target = BlockedPackage, validUntil = Now - 1_000)),
                    ),
                context = appContext(packageName = BlockedPackage, usedMinutesToday = 100),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `uses active global extra time for domain`() {
        val decision =
            engine.evaluateDomain(
                snapshot =
                    policy(
                        rules = listOf(domainRule(target = "blocked.test", action = RuleAction.Block)),
                        grants =
                            listOf(
                                grant(
                                    targetType = PolicyTargetType.Global,
                                    target = "extra_time",
                                    validUntil = Now + 1_000,
                                ),
                            ),
                    ),
                context = domainContext(domain = "blocked.test"),
            )

        assertIs<PolicyDecision.GrantExtraTime>(decision)
    }

    @Test
    fun `requires activation when device is not activated`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(),
                context = appContext(isActivated = false),
            )

        assertIs<PolicyDecision.RequireActivation>(decision)
    }

    @Test
    fun `requests authorization when rule requires it`() {
        val decision =
            engine.evaluateApp(
                snapshot =
                    policy(
                        rules = listOf(rule(target = BlockedPackage, action = RuleAction.RequestAuthorization)),
                    ),
                context = appContext(packageName = BlockedPackage),
            )

        assertIs<PolicyDecision.RequestAuthorization>(decision)
    }

    @Test
    fun `uses cached rules when sync has warnings`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(rules = listOf(rule(target = BlockedPackage, action = RuleAction.Block))),
                context = appContext(packageName = BlockedPackage, syncState = ComponentState.Warning),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    private fun policy(
        rules: List<PolicyRule> = emptyList(),
        limits: List<DailyLimit> = emptyList(),
        grants: List<ExtraTimeGrant> = emptyList(),
    ): PolicySnapshot =
        PolicySnapshot(
            id = "policy",
            version = 1,
            rules = rules,
            dailyLimits = limits,
            extraTimeGrants = grants,
        )

    private fun rule(
        id: String = "rule",
        level: PolicyLevel = PolicyLevel.Account,
        scope: RuleScope = RuleScope.App,
        target: String = BlockedPackage,
        action: RuleAction,
        priority: Int = 0,
    ): PolicyRule =
        PolicyRule(
            id = id,
            level = level,
            scope = scope,
            target = target,
            action = action,
            priority = priority,
            enabled = true,
        )

    private fun domainRule(
        id: String = "rule",
        target: String,
        action: RuleAction,
        priority: Int = 0,
    ): PolicyRule = rule(id = id, scope = RuleScope.Domain, target = target, action = action, priority = priority)

    private fun limit(
        target: String,
        minutes: Int,
    ): DailyLimit =
        DailyLimit(
            id = "limit-$target",
            targetType = PolicyTargetType.App,
            target = target,
            limitMinutes = minutes,
            enabled = true,
        )

    private fun grant(
        targetType: PolicyTargetType = PolicyTargetType.App,
        target: String,
        validUntil: Long,
    ): ExtraTimeGrant =
        ExtraTimeGrant(
            id = "grant-$target",
            requestId = null,
            targetType = targetType,
            target = target,
            grantedMinutes = 15,
            validUntilEpochMillis = validUntil,
        )

    private fun appContext(
        packageName: String = AllowedPackage,
        usedMinutesToday: Int = 0,
        isActivated: Boolean = true,
        syncState: ComponentState = ComponentState.Enabled,
    ): AppPolicyContext =
        AppPolicyContext(
            packageName = packageName,
            category = null,
            usedMinutesToday = usedMinutesToday,
            time = TimePolicyContext(evaluatedAtEpochMillis = Now, minuteOfDay = 720),
            device = deviceContext(isActivated = isActivated, syncState = syncState),
        )

    private fun domainContext(
        domain: String,
        sourceDomain: String? = null,
        topLevelNavigation: Boolean = false,
    ): DomainPolicyContext =
        DomainPolicyContext(
            domain = domain,
            category = null,
            time = TimePolicyContext(evaluatedAtEpochMillis = Now, minuteOfDay = 720),
            device = deviceContext(isActivated = true),
            sourceDomain = sourceDomain,
            isTopLevelNavigation = topLevelNavigation,
        )

    private fun deviceContext(
        isActivated: Boolean,
        syncState: ComponentState = ComponentState.Enabled,
    ): DevicePolicyContext =
        DevicePolicyContext(
            isActivated = isActivated,
            healthSnapshot =
                SystemHealthSnapshot(
                    vpnState = ComponentState.Enabled,
                    accessibilityState = ComponentState.Enabled,
                    syncState = syncState,
                    integrityState = ComponentState.Enabled,
                    databaseState = ComponentState.Enabled,
                    licenseState = LicenseState.Active,
                    updateState = UpdateState.Current,
                    checkedAtEpochMillis = Now,
                ),
        )

    private companion object {
        const val AllowedPackage = "com.example.allowed"
        const val BlockedPackage = "com.example.blocked"
        const val Now = 1_000_000L
    }
}
