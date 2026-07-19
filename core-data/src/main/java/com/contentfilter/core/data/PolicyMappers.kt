package com.contentfilter.core.data

import com.contentfilter.core.database.entity.PolicyRuleEntity
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTimeWindow
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope

internal fun PolicyRuleEntity.toDomain(): PolicyRule =
    PolicyRule(
        id = id,
        scope = enumValueOrDefault(scope, RuleScope.Global),
        target = target,
        action = enumValueOrDefault(action, RuleAction.Warn),
        priority = priority,
        enabled = enabled,
        activeWindow =
            activeWindowStartMinute?.let { start ->
                activeWindowEndMinute?.let { end -> PolicyTimeWindow(start, end) }
            },
        activeDaysMask = activeDaysMask,
    )

internal fun PolicyRule.toEntity(
    policyId: String,
    updatedAtEpochMillis: Long,
): PolicyRuleEntity =
    PolicyRuleEntity(
        id = id,
        policyId = policyId,
        scope = scope.name,
        target = target,
        action = action.name,
        priority = priority,
        enabled = enabled,
        activeWindowStartMinute = activeWindow?.startMinuteOfDay,
        activeWindowEndMinute = activeWindow?.endMinuteOfDay,
        activeDaysMask = activeDaysMask,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default
