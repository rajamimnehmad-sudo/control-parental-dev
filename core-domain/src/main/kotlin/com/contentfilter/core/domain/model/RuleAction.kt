package com.contentfilter.core.domain.model

/**
 * Actions a rule may request. The PolicyEngine decides final outcomes.
 */
enum class RuleAction {
    Allow,
    Block,
    Warn,
    RequestAuthorization,
}
