package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import kotlin.test.Test
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
}
