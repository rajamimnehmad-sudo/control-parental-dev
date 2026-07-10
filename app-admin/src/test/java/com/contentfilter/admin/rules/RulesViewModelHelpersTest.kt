package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.webImagesBlocked
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesViewModelHelpersTest {
    @Test
    fun `web block plan writes the main rule first`() {
        val changes =
            emptyList<PolicyRule>().webNavigationModeChanges(
                blocked = true,
                imagesBlocked = false,
                safeSearchEnabled = true,
            )

        assertEquals(WebNavigationPolicy.RuleTarget, changes.first().target)
        assertTrue(changes.first().enabled)
        assertTrue(changes.applyTo(emptyList()).webNavigationBlocked())
    }

    @Test
    fun `web allow plan disables stale wildcard search dns and image blocks`() {
        val current =
            listOf(
                domainRule(WebNavigationPolicy.RuleTarget, RuleAction.Block, enabled = true),
                domainRule(DomainWildcard, RuleAction.Block, enabled = true),
                domainRule(SearchEngineDomains.first(), RuleAction.Block, enabled = true),
                domainRule(SecureDnsDomains.first(), RuleAction.Block, enabled = true),
                domainRule(WebNavigationPolicy.ImagesBlockedTarget, RuleAction.Block, enabled = true),
                domainRule(WebNavigationPolicy.ImageDomains.first(), RuleAction.Block, enabled = true),
                domainRule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow, enabled = true),
            )

        val changes =
            current.webNavigationModeChanges(
                blocked = false,
                imagesBlocked = true,
                safeSearchEnabled = true,
            )
        val result = changes.applyTo(current)

        assertEquals(WebNavigationPolicy.RuleTarget, changes.first().target)
        assertFalse(result.webNavigationBlocked())
        assertFalse(result.webImagesBlocked())
        assertFalse(result.any { it.enabled && it.action == RuleAction.Block && it.target == DomainWildcard })
        assertFalse(result.any { it.enabled && it.action == RuleAction.Block && it.target in SearchProtectionDomains })
        assertFalse(
            result.any {
                it.enabled && it.action == RuleAction.Block && WebNavigationPolicy.isImageDomain(it.target)
            },
        )
    }

    @Test
    fun `does not confirm blocked search engines until all search protection domains are blocked`() {
        val rules = SearchProtectionDomains.dropLast(1).map { domainRule(it, RuleAction.Block, enabled = true) }

        assertFalse(rules.searchEnginesStateConfirmed(allowed = false))
    }

    @Test
    fun `confirms blocked search engines only when search and secure dns domains are blocked without stale allows`() {
        val rules = SearchProtectionDomains.map { domainRule(it, RuleAction.Block, enabled = true) }

        assertTrue(rules.searchEnginesStateConfirmed(allowed = false))
    }

    @Test
    fun `does not confirm blocked search engines while stale allow remains enabled`() {
        val rules =
            SearchProtectionDomains.map { domainRule(it, RuleAction.Block, enabled = true) } +
                domainRule(SearchEngineDomains.first(), RuleAction.Allow, enabled = true)

        assertFalse(rules.searchEnginesStateConfirmed(allowed = false))
    }

    @Test
    fun `allowed search engines requires no active search protection block`() {
        val rules = listOf(domainRule(SearchEngineDomains.first(), RuleAction.Block, enabled = true))

        assertFalse(rules.searchEnginesStateConfirmed(allowed = true))
    }

    @Test
    fun `visible search state includes secure dns protection blocks`() {
        val rules = listOf(domainRule(SecureDnsDomains.first(), RuleAction.Block, enabled = true))

        assertFalse(rules.searchEnginesAllowed())
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
    ): RemoteInstalledAppDto =
        RemoteInstalledAppDto(
            id = packageName,
            accountId = AccountId,
            deviceId = DeviceId,
            appName = name,
            packageName = packageName,
            versionName = null,
            isSystemApp = false,
            iconBase64 = null,
            updatedAt = "2026-07-06T00:00:00Z",
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
