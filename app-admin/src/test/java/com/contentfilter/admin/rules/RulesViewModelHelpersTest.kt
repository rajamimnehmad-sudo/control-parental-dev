package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesViewModelHelpersTest {
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
                devices = listOf(Device(id = DeviceId, accountId = AccountId, displayName = "Usuario")),
                pendingAllowed = emptyMap(),
            )

        assertEquals(
            listOf("com.example.zeta", "com.example.middle", "com.example.alpha"),
            controls.map { it.packageName },
        )
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

    private companion object {
        const val AccountId = "account"
        const val DeviceId = "device"
    }
}
