package com.contentfilter.core.domain.model

object PolicySchedulePolicy {
    const val TargetPrefix = "__schedule_allowed__:"
    const val RulePriority = 4_000
    const val WildcardTarget = "*"

    fun PolicyRule.isScheduleRule(): Boolean =
        target.startsWith(TargetPrefix) &&
            action == RuleAction.Allow &&
            activeWindow != null

    fun PolicyRule.isAllowedWindow(): Boolean = enabled && isScheduleRule()

    fun encodedTarget(target: String): String = "$TargetPrefix$target"

    fun PolicyRule.scheduleTarget(): String? =
        target.removePrefix(TargetPrefix).takeIf { isScheduleRule() && it.isNotBlank() }
}
