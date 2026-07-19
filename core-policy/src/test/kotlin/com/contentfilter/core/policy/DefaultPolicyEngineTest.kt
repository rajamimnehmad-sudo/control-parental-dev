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
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.PolicyTimeWindow
import com.contentfilter.core.domain.model.PolicyWeekdays
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.WebNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `scheduled app wildcard allows every app only on configured days`() {
        val scheduleRule =
            rule(
                id = "schedule-global-app",
                target = PolicySchedulePolicy.encodedTarget("*"),
                action = RuleAction.Allow,
            ).copy(
                priority = PolicySchedulePolicy.RulePriority,
                activeWindow = PolicyTimeWindow(22 * 60, 23 * 60 + 59),
                activeDaysMask = PolicyWeekdays.bit(3),
            )
        val wednesdayNight =
            appContext().copy(time = TimePolicyContext(Now, 22 * 60 + 30, isoDayOfWeek = 3))
        val thursdayNight =
            appContext().copy(time = TimePolicyContext(Now, 22 * 60 + 30, isoDayOfWeek = 4))

        assertIs<PolicyDecision.Allow>(engine.evaluateApp(policy(rules = listOf(scheduleRule)), wednesdayNight))
        assertIs<PolicyDecision.Block>(engine.evaluateApp(policy(rules = listOf(scheduleRule)), thursdayNight))
    }

    @Test
    fun `global and per app schedules use the most restrictive intersection`() {
        val global = allowedSchedule("global", RuleScope.App, "*", 8 * 60, 18 * 60)
        val app = allowedSchedule("app", RuleScope.App, AllowedPackage, 10 * 60, 20 * 60)

        listOf(9 * 60, 19 * 60).forEach { minute ->
            val context = appContext().copy(time = TimePolicyContext(Now, minute, isoDayOfWeek = 1))
            assertIs<PolicyDecision.Block>(engine.evaluateApp(policy(rules = listOf(global, app)), context))
        }
        val sharedWindow = appContext().copy(time = TimePolicyContext(Now, 12 * 60, isoDayOfWeek = 1))
        assertIs<PolicyDecision.Allow>(engine.evaluateApp(policy(rules = listOf(global, app)), sharedWindow))
    }

    @Test
    fun `domain schedule blocks only the configured site outside its window`() {
        val schedule = allowedSchedule("site", RuleScope.Domain, "example.com", 8 * 60, 12 * 60)
        val outside =
            domainContext("www.example.com").copy(
                time = TimePolicyContext(Now, 12 * 60, isoDayOfWeek = 1),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(policy(rules = listOf(schedule)), outside))
        assertIs<PolicyDecision.Allow>(
            engine.evaluateDomain(policy(rules = listOf(schedule)), domainContext("other.com")),
        )
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
    fun `blocks app when daily limit is exactly reached`() {
        val decision =
            engine.evaluateApp(
                snapshot = policy(limits = listOf(limit(target = AllowedPackage, minutes = 60))),
                context = appContext(packageName = AllowedPackage, usedMinutesToday = 60),
            )

        assertIs<PolicyDecision.Block>(decision)
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
    fun `manual google block wins when web navigation is open`() {
        val snapshot = policy(rules = listOf(domainRule(target = "google.com", action = RuleAction.Block)))

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("www.google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("search.google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("clients4.google.com")))
    }

    @Test
    fun `manual regional google block wins when web navigation is open`() {
        val snapshot = policy(rules = listOf(domainRule(target = "google.com.ar", action = RuleAction.Block)))

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("google.com.ar")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("www.google.com.ar")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("search.google.com.ar")))
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
    fun `safe default snapshot keeps search open while enforcing safe DNS`() {
        val snapshot = SearchProtectionPolicyDefaults.safeDefaultSnapshot()

        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("clients4.google.com")))
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("dns.google")))
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
    fun `SafeSearch blocks known encrypted DNS bootstrap hosts`() {
        val safeSearch =
            policy(
                rules =
                    listOf(
                        domainRule(target = WebNavigationPolicy.SafeSearchTarget, action = RuleAction.Allow),
                    ),
            )
        listOf("dns.google", "chrome.cloudflare-dns.com", "dns9.quad9.net").forEach { host ->
            assertIs<PolicyDecision.Block>(engine.evaluateDomain(safeSearch, domainContext(host)))
        }
        assertIs<PolicyDecision.Allow>(engine.evaluateDomain(safeSearch, domainContext("example.com")))
    }

    @Test
    fun `Solo resultados blocks every external navigation entry point`() {
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

        listOf(
            domainContext("example.com", sourceDomain = "google.com", topLevelNavigation = true),
            domainContext("example.com", sourceDomain = null, topLevelNavigation = true),
            domainContext("example.com", sourceDomain = null, topLevelNavigation = false),
            domainContext("example.com", sourceDomain = "example.com", topLevelNavigation = true),
        ).forEach { context ->
            assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, context))
        }
    }

    @Test
    fun `Solo resultados ignores manual domain exceptions`() {
        val snapshot =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                        domainRule(target = "example.com", action = RuleAction.Allow, priority = 10_000),
                    ),
            )

        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("example.com")))
    }

    @Test
    fun `Solo resultados allows search engines and required support hosts`() {
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

        listOf(
            "google.com",
            "bing.com",
            "search.yahoo.com",
            "duckduckgo.com",
            "fonts.gstatic.com",
            "forcesafesearch.google.com",
        ).forEach { host ->
            assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext(host)), host)
        }
    }

    @Test
    fun `open mode uses the canonical search technical allowlist`() {
        val snapshot = policy()

        listOf(
            "google.com",
            "google.com.ar",
            "clients4.google.com",
            "clientservices.googleapis.com",
            "fonts.googleapis.com",
            "www.gstatic.com",
            "googleusercontent.com",
            "forcesafesearch.google.com",
            "bing.com",
            "bing.net",
            "search.yahoo.com",
            "yimg.com",
            "duckduckgo.com",
            "duck.com",
        ).forEach { host ->
            assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext(host)), host)
        }
    }

    @Test
    fun `open mode forces SafeSearch only on supported search engines`() {
        val snapshot = policy()

        val google = assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("google.com")))
        val googleSupport =
            assertIs<PolicyDecision.Allow>(engine.evaluateDomain(snapshot, domainContext("fonts.gstatic.com")))

        assertTrue(google.safeSearchRequired)
        assertTrue(!googleSupport.safeSearchRequired)
        assertIs<PolicyDecision.Block>(engine.evaluateDomain(snapshot, domainContext("dns.google")))
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
    fun `legacy SafeSearch off is ignored without changing external result navigation`() {
        val safeSearchOff =
            policy(
                rules =
                    listOf(
                        domainRule(
                            target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                        domainRule(
                            target = WebNavigationPolicy.SafeSearchTarget,
                            action = RuleAction.Allow,
                        ).copy(enabled = false),
                    ),
            )
        val decision = assertIs<PolicyDecision.Allow>(engine.evaluateDomain(safeSearchOff, domainContext("bing.com")))

        assertTrue(decision.safeSearchRequired)
        assertIs<PolicyDecision.Block>(
            engine.evaluateDomain(
                safeSearchOff,
                domainContext("example.com", sourceDomain = "bing.com", topLevelNavigation = true),
            ),
        )
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
    fun `all Web protection layers remain cumulative across the complete matrix`() {
        repeat(8) { bits ->
            val webBlocked = bits and 1 != 0
            val externalResultsAllowed = bits and 2 != 0
            val safeSearchEnabled = true
            val snapshot = policy(rules = webRules(bits))

            val searchDecision = engine.evaluateDomain(snapshot, domainContext("google.com"))
            if (webBlocked) {
                assertIs<PolicyDecision.Block>(searchDecision)
                return@repeat
            }

            val searchAllowed = assertIs<PolicyDecision.Allow>(searchDecision)
            assertEquals(safeSearchEnabled, searchAllowed.safeSearchRequired)

            val externalDecision =
                engine.evaluateDomain(
                    snapshot,
                    domainContext("example.com", sourceDomain = "google.com", topLevelNavigation = true),
                )
            if (externalResultsAllowed) {
                assertIs<PolicyDecision.Allow>(externalDecision)
            } else {
                assertIs<PolicyDecision.Block>(externalDecision)
            }

            val encryptedDnsDecision = engine.evaluateDomain(snapshot, domainContext("dns.google"))
            if (!externalResultsAllowed || safeSearchEnabled) {
                assertIs<PolicyDecision.Block>(encryptedDnsDecision)
            } else {
                assertIs<PolicyDecision.Allow>(encryptedDnsDecision)
            }
        }
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
                        if (rule.target == WebNavigationPolicy.RuleTarget || rule.target == "google.com") {
                            rule.copy(enabled = false)
                        } else {
                            rule
                        }
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

    private fun webRules(bits: Int): List<PolicyRule> =
        listOf(
            domainRule(
                id = "web-blocked",
                target = WebNavigationPolicy.RuleTarget,
                action = RuleAction.Block,
            ).copy(enabled = bits and 1 != 0),
            domainRule(
                id = "external-results",
                target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                action = RuleAction.Allow,
            ).copy(enabled = bits and 2 != 0),
            domainRule(
                id = "safe-search",
                target = WebNavigationPolicy.SafeSearchTarget,
                action = RuleAction.Allow,
            ).copy(enabled = bits and 4 != 0),
        )

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

    private fun allowedSchedule(
        id: String,
        scope: RuleScope,
        target: String,
        startMinute: Int,
        endMinute: Int,
    ): PolicyRule =
        rule(
            id = id,
            scope = scope,
            target = PolicySchedulePolicy.encodedTarget(target),
            action = RuleAction.Allow,
            priority = PolicySchedulePolicy.RulePriority,
        ).copy(
            activeWindow = PolicyTimeWindow(startMinute, endMinute),
            activeDaysMask = PolicyWeekdays.All,
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
