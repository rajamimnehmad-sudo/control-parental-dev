package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.webNavigationBlocked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesViewModelHelpersTest {
    @Test
    fun `web policy plan writes the main rule first with deterministic ids`() {
        val changes =
            emptyList<PolicyRule>().webPolicyChanges(
                desired = preferences(webBlocked = true),
                deviceId = DeviceId,
            )

        assertEquals(WebNavigationPolicy.RuleTarget, changes.first().target)
        assertTrue(changes.first().enabled)
        assertEquals(
            webRuleId(DeviceId, WebNavigationPolicy.RuleTarget, RuleAction.Block),
            changes.first().id,
        )
        assertTrue(changes.applyTo(emptyList()).webNavigationBlocked())
    }

    @Test
    fun `all canonical web rule ids are stable for the selected device`() {
        val first =
            emptyList<PolicyRule>().webPolicyChanges(
                desired = preferences(webBlocked = true, externalResultsAllowed = true),
                deviceId = DeviceId,
            )
        val second =
            emptyList<PolicyRule>().webPolicyChanges(
                desired = preferences(webBlocked = false, externalResultsAllowed = false),
                deviceId = DeviceId,
            )

        assertEquals(first.map { it.id }.take(3), second.map { it.id }.take(3))
    }

    @Test
    fun `repair migrates legacy preference and disables wildcard search and dns blocks`() {
        val current =
            listOf(
                domainRule(WebNavigationPolicy.RuleTarget, RuleAction.Block, enabled = true),
                domainRule(DomainWildcard, RuleAction.Block, enabled = true),
                domainRule(SearchEngineDomains.first(), RuleAction.Block, enabled = true),
                domainRule(SearchSupportDomains.first(), RuleAction.Block, enabled = true),
                domainRule(SecureDnsDomains.first(), RuleAction.Block, enabled = true),
                domainRule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow, enabled = true),
                domainRule(WebNavigationPolicy.LegacyGoogleResultsAllowedTarget, RuleAction.Allow, enabled = true),
            )
        val changes =
            current.webPolicyChanges(
                desired = current.webPolicyPreferences().copy(webNavigationBlocked = false),
                deviceId = DeviceId,
            )
        val result = changes.applyTo(current)

        assertEquals(WebNavigationPolicy.RuleTarget, changes.first().target)
        assertFalse(result.webNavigationBlocked())
        assertTrue(result.externalSearchResultsAllowedForWeb())
        assertTrue(result.safeSearchEnabledForWeb())
        assertFalse(result.any { it.enabled && it.action == RuleAction.Block && it.target == DomainWildcard })
        assertEquals(0, result.activeWebAuxiliaryBlockCount())
        assertFalse(result.any { it.enabled && it.target == WebNavigationPolicy.LegacyGoogleResultsAllowedTarget })
    }

    @Test
    fun `repair disables every random duplicate and keeps one deterministic canonical rule`() {
        val current =
            listOf(
                domainRule(WebNavigationPolicy.RuleTarget, RuleAction.Block, enabled = true),
                domainRule(
                    WebNavigationPolicy.RuleTarget,
                    RuleAction.Block,
                    enabled = true,
                ).copy(id = "main-duplicate"),
                domainRule("clients4.google.com", RuleAction.Block, enabled = true),
                domainRule("gstatic.com", RuleAction.Block, enabled = true),
                domainRule("dns.google", RuleAction.Block, enabled = true),
            )
        val result =
            current.webPolicyChanges(
                desired = preferences(webBlocked = false),
                deviceId = DeviceId,
            ).applyTo(current)

        assertFalse(result.webNavigationBlocked())
        assertEquals(
            1,
            result.count {
                it.id == webRuleId(DeviceId, WebNavigationPolicy.RuleTarget, RuleAction.Block)
            },
        )
        assertEquals(0, result.activeWebAuxiliaryBlockCount())
    }

    @Test
    fun `complete preference matrix remains independent and has no auxiliary blocks`() {
        val combinations = (0 until 4).map(::preferencesFromBits)

        combinations.forEach { desired ->
            val result =
                contaminatedOpenWebRules()
                    .webPolicyChanges(desired, DeviceId)
                    .applyTo(contaminatedOpenWebRules())

            assertEquals(desired, result.webPolicyPreferences())
            assertEquals(0, result.activeWebAuxiliaryBlockCount())
        }
    }

    @Test
    fun `every Web mutation changes only its selected preference`() {
        (0 until 4).map(::preferencesFromBits).forEach { initial ->
            val initialRules =
                emptyList<PolicyRule>()
                    .webPolicyChanges(initial, DeviceId)
                    .applyTo(emptyList())

            listOf(
                WebPolicyPreference.NavigationBlocked,
                WebPolicyPreference.ExternalSearchResultsAllowed,
            ).forEach { preference ->
                listOf(false, true).forEach { enabled ->
                    val result =
                        initialRules
                            .webPolicyPreferenceChanges(preference, enabled, DeviceId)
                            .applyTo(initialRules)

                    assertEquals(initial.withPreference(preference, enabled), result.webPolicyPreferences())
                    assertEquals(0, result.activeWebAuxiliaryBlockCount())
                }
            }
        }
    }

    @Test
    fun `legacy SafeSearch off is ignored while Solo resultados persists independently`() {
        var rules =
            emptyList<PolicyRule>()
                .webPolicyChanges(
                    preferences(webBlocked = false, externalResultsAllowed = true, safeSearch = false),
                    DeviceId,
                ).applyTo(emptyList())

        rules =
            rules
                .webPolicyPreferenceChanges(WebPolicyPreference.SafeSearchEnabled, true, DeviceId)
                .applyTo(rules)
        assertTrue(rules.safeSearchEnabledForWeb())
        assertTrue(rules.externalSearchResultsAllowedForWeb())

        rules =
            rules
                .webPolicyPreferenceChanges(WebPolicyPreference.ExternalSearchResultsAllowed, false, DeviceId)
                .applyTo(rules)
        assertTrue(rules.safeSearchEnabledForWeb())
        assertFalse(rules.externalSearchResultsAllowedForWeb())

        rules =
            rules
                .webPolicyPreferenceChanges(WebPolicyPreference.SafeSearchEnabled, false, DeviceId)
                .applyTo(rules)
        assertTrue(rules.safeSearchEnabledForWeb())
        assertFalse(rules.externalSearchResultsAllowedForWeb())
    }

    @Test
    fun `failed layer rollback clears only that pending state`() {
        val bothPending =
            RulesUiState(
                pendingExternalSearchResultsAllowed = false,
                pendingSafeSearchEnabled = true,
            )

        val safeSearchFailed = bothPending.clearPendingWebPreference(WebPolicyPreference.SafeSearchEnabled)
        assertEquals(false, safeSearchFailed.pendingExternalSearchResultsAllowed)
        assertEquals(null, safeSearchFailed.pendingSafeSearchEnabled)

        val onlyResultsFailed = bothPending.clearPendingWebPreference(WebPolicyPreference.ExternalSearchResultsAllowed)
        assertEquals(null, onlyResultsFailed.pendingExternalSearchResultsAllowed)
        assertEquals(true, onlyResultsFailed.pendingSafeSearchEnabled)
    }

    @Test
    fun `removed image rule is ignored without resetting valid Web preferences`() {
        val oldImageRule = domainRule("__web_images_blocked__", RuleAction.Block, enabled = true)
        val rules =
            listOf(
                oldImageRule,
                domainRule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow, enabled = true),
                domainRule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow, enabled = false),
            )

        assertEquals(
            preferences(webBlocked = false, externalResultsAllowed = false, safeSearch = true),
            rules.webPolicyPreferences(),
        )
        assertTrue(rules.webPolicyChanges(rules.webPolicyPreferences(), DeviceId).none { it.id == oldImageRule.id })
    }

    @Test
    fun `each Web pending state is isolated from the other switches`() {
        WebPolicyPreference.entries.forEach { preference ->
            val pending = RulesUiState().withPendingWebPreference(preference, true)

            assertEquals(preference == WebPolicyPreference.NavigationBlocked, pending.pendingInternetBlocked == true)
            assertEquals(
                preference == WebPolicyPreference.ExternalSearchResultsAllowed,
                pending.pendingExternalSearchResultsAllowed == true,
            )
            assertEquals(preference == WebPolicyPreference.SafeSearchEnabled, pending.pendingSafeSearchEnabled == true)
            assertEquals(RulesUiState(), pending.clearPendingWebPreference(preference))
        }
    }

    @Test
    fun `Internet selector maps open left and blocked right without changing layers`() {
        val open =
            RulesUiState(
                internetBlocked = false,
                externalSearchResultsAllowed = false,
                safeSearchEnabled = true,
            )
        val blocked = open.copy(internetBlocked = true)

        assertEquals(InternetMode.Open, open.internetMode)
        assertEquals(InternetMode.Blocked, blocked.internetMode)
        assertTrue(open.webLayersVisible)
        assertFalse(blocked.webLayersVisible)
        assertTrue(blocked.onlyResultsEnabled)
        assertTrue(blocked.safeSearchEnabled)
    }

    @Test
    fun `Web summary distinguishes fully open protected and blocked states`() {
        val fullyOpen = RulesUiState(externalSearchResultsAllowed = true).webPanelPresentation()
        val protected =
            RulesUiState(
                externalSearchResultsAllowed = false,
                safeSearchEnabled = true,
            ).webPanelPresentation()
        val blocked = RulesUiState(internetBlocked = true, safeSearchEnabled = true).webPanelPresentation()

        assertEquals("Internet abierto con protecciones", fullyOpen.headline)
        assertEquals(listOf("SafeSearch"), fullyOpen.activeLayers)
        assertEquals("Internet abierto con protecciones", protected.headline)
        assertEquals(listOf("SafeSearch", "Solo resultados"), protected.activeLayers)
        assertTrue(protected.showLayers)
        assertEquals("Internet bloqueado", blocked.headline)
        assertFalse(blocked.showLayers)
    }

    @Test
    fun `blocking and unblocking web preserves every secondary preference`() {
        var rules =
            emptyList<PolicyRule>()
                .webPolicyChanges(
                    preferences(
                        webBlocked = false,
                        externalResultsAllowed = false,
                        safeSearch = true,
                    ),
                    DeviceId,
                ).applyTo(emptyList())

        rules =
            rules
                .webPolicyPreferenceChanges(WebPolicyPreference.NavigationBlocked, true, DeviceId)
                .applyTo(rules)
        assertTrue(rules.webNavigationBlocked())
        assertFalse(rules.externalSearchResultsAllowedForWeb())
        assertTrue(rules.safeSearchEnabledForWeb())

        rules =
            rules
                .webPolicyPreferenceChanges(WebPolicyPreference.NavigationBlocked, false, DeviceId)
                .applyTo(rules)
        assertTrue(rules.webNavigationOpenWithoutAuxiliaryBlocks())
        assertFalse(rules.externalSearchResultsAllowedForWeb())
        assertTrue(rules.safeSearchEnabledForWeb())
    }

    @Test
    fun `five full web cycles remain canonical and idempotent`() {
        var rules = contaminatedOpenWebRules()

        repeat(5) { cycle ->
            val restricted =
                preferences(
                    webBlocked = false,
                    externalResultsAllowed = false,
                    safeSearch = true,
                )
            rules = rules.webPolicyChanges(restricted, DeviceId).applyTo(rules)
            assertEquals(restricted, rules.webPolicyPreferences())

            val released = restricted.copy(externalSearchResultsAllowed = true)
            rules = rules.webPolicyChanges(released, DeviceId).applyTo(rules)
            val blocked = released.copy(webNavigationBlocked = true)
            rules = rules.webPolicyChanges(blocked, DeviceId).applyTo(rules)
            assertTrue(rules.webNavigationBlocked())
            rules = rules.webPolicyChanges(released, DeviceId).applyTo(rules)
            assertEquals(released, rules.webPolicyPreferences())
            assertTrue(rules.webNavigationOpenWithoutAuxiliaryBlocks())
            assertTrue(rules.webPolicyChanges(released, DeviceId).isEmpty())
        }
    }

    @Test
    fun `app controls sort blocked apps before limited and allowed apps`() {
        val controls =
            listOf(
                remoteApp("Zeta", "com.example.zeta"),
                remoteApp("Alpha", "com.example.alpha"),
                remoteApp("Middle", "com.example.middle"),
            ).toAppControls(
                rules = listOf(appRule("com.example.zeta", RuleAction.Block)),
                limits =
                    listOf(
                        DailyLimit(
                            id = "limit-middle",
                            targetType = PolicyTargetType.App,
                            target = "com.example.middle",
                            limitMinutes = 15,
                            enabled = true,
                        ),
                    ),
                grants = emptyList(),
                appGroups = emptyList(),
                nowEpochMillis = NowEpochMillis,
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = emptyMap(),
            )

        assertEquals(
            listOf("com.example.zeta", "com.example.middle", "com.example.alpha"),
            controls.map { it.packageName },
        )
    }

    @Test
    fun `app controls expose active extra time remaining and sort before allowed apps`() {
        val controls =
            listOf(
                remoteApp("Allowed", "com.example.allowed"),
                remoteApp("Extra", "com.example.extra"),
            ).toAppControls(
                rules = listOf(appRule("com.example.extra", RuleAction.Block)),
                limits = emptyList(),
                grants =
                    listOf(
                        ExtraTimeGrant(
                            id = "grant-extra",
                            requestId = "request-extra",
                            targetType = PolicyTargetType.App,
                            target = "com.example.extra",
                            grantedMinutes = 10,
                            validUntilEpochMillis = NowEpochMillis + 10 * MinuteMillis,
                        ),
                    ),
                appGroups = emptyList(),
                nowEpochMillis = NowEpochMillis,
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = emptyMap(),
            )

        assertEquals(listOf("com.example.extra", "com.example.allowed"), controls.map { it.packageName })
        assertEquals(10, controls.first().extraTimeRemainingMinutes)
        assertTrue(controls.first().allowed)
    }

    @Test
    fun `cached app order stays stable when remote merge order changes`() {
        val first =
            listOf(
                remoteApp("Zeta", "com.example.zeta"),
                remoteApp("Alpha", "com.example.alpha"),
            ).toAppControls(
                rules = emptyList(),
                limits = emptyList(),
                grants = emptyList(),
                appGroups = emptyList(),
                nowEpochMillis = NowEpochMillis,
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = emptyMap(),
            )
        val refreshed =
            listOf(
                remoteApp("Alpha", "com.example.alpha"),
                remoteApp("Zeta", "com.example.zeta"),
            ).toAppControls(
                rules = emptyList(),
                limits = emptyList(),
                grants = emptyList(),
                appGroups = emptyList(),
                nowEpochMillis = NowEpochMillis,
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = emptyMap(),
            )

        assertEquals(first.map { it.packageName }, refreshed.map { it.packageName })
    }

    @Test
    fun `pending app state survives cached inventory refresh`() {
        val control =
            listOf(remoteApp("Example", "com.example.app")).toAppControls(
                rules = listOf(appRule("com.example.app", RuleAction.Block)),
                limits = emptyList(),
                grants = emptyList(),
                appGroups = emptyList(),
                nowEpochMillis = NowEpochMillis,
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = mapOf("com.example.app" to true),
            ).single()

        assertTrue(control.allowed)
        assertTrue(control.isUpdating)
    }

    private fun domainRule(
        target: String,
        action: RuleAction,
        enabled: Boolean,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target-$action-$enabled",
            level = PolicyLevel.Account,
            scope = RuleScope.Domain,
            target = target,
            action = action,
            priority = 100,
            enabled = enabled,
        )

    private fun contaminatedOpenWebRules(): List<PolicyRule> =
        listOf(
            domainRule(WebNavigationPolicy.RuleTarget, RuleAction.Block, enabled = true),
            domainRule(WebNavigationPolicy.RuleTarget, RuleAction.Block, enabled = true).copy(id = "main-duplicate"),
            domainRule(DomainWildcard, RuleAction.Block, enabled = true),
            domainRule("google.com", RuleAction.Block, enabled = true),
            domainRule("gstatic.com", RuleAction.Block, enabled = true),
            domainRule("dns.google", RuleAction.Block, enabled = true),
        )

    private fun preferences(
        webBlocked: Boolean,
        externalResultsAllowed: Boolean = false,
        safeSearch: Boolean = true,
    ): WebPolicyPreferences =
        WebPolicyPreferences(
            webNavigationBlocked = webBlocked,
            externalSearchResultsAllowed = externalResultsAllowed,
            safeSearchEnabled = true,
        )

    private fun preferencesFromBits(bits: Int): WebPolicyPreferences =
        preferences(
            webBlocked = bits and 1 != 0,
            externalResultsAllowed = bits and 2 != 0,
            safeSearch = true,
        )

    private fun appRule(
        target: String,
        action: RuleAction,
        enabled: Boolean = true,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target-$action-$enabled",
            level = PolicyLevel.Device,
            scope = RuleScope.App,
            target = target,
            action = action,
            priority = 100,
            enabled = enabled,
        )

    private fun remoteApp(
        name: String,
        packageName: String,
    ): InstalledApp =
        InstalledApp(
            id = packageName,
            accountId = AccountId,
            deviceId = DeviceId,
            appName = name,
            packageName = packageName,
            versionName = null,
            isSystemApp = false,
            iconBase64 = null,
            updatedAtEpochMillis = 1L,
        )

    private fun List<PolicyRule>.applyTo(current: List<PolicyRule>): List<PolicyRule> {
        val changesById = associateBy { it.id }
        return current.map { changesById[it.id] ?: it } + filter { change -> current.none { it.id == change.id } }
    }

    private companion object {
        const val AccountId = "account"
        const val DeviceId = "device"
        const val NowEpochMillis = 1_000_000L
    }
}
