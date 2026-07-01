package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction

data class RulesUiState(
    val rules: List<PolicyRule> = emptyList(),
    val target: String = "",
    val selectedAction: RuleAction = RuleAction.Block,
    val offlineMode: Boolean = true,
    val message: String = "",
)
