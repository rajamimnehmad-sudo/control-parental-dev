package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope

internal class ForegroundDecisionDiagnosticGate {
    private var lastKey: String? = null

    fun shouldRecord(key: String): Boolean {
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

internal fun hasExplicitAppApproval(
    packageName: String,
    rules: List<PolicyRule>,
): Boolean =
    rules.any {
        it.enabled &&
            it.scope == RuleScope.App &&
            it.target == packageName &&
            it.action == RuleAction.Allow
    }

internal fun Long.toObservedMinutes(): Int =
    if (this <= 0L) {
        0
    } else {
        ((this + MillisPerMinute - 1) / MillisPerMinute).toInt()
    }

internal fun PolicyDecision.label(): String =
    when (this) {
        is PolicyDecision.Allow -> "Allow"
        is PolicyDecision.Block -> "Block"
        is PolicyDecision.GrantExtraTime -> "GrantExtraTime"
        is PolicyDecision.HealthWarning -> "HealthWarning"
        is PolicyDecision.RequestAuthorization -> "RequestAuthorization"
        is PolicyDecision.RequireActivation -> "RequireActivation"
        is PolicyDecision.RequireUpdate -> "RequireUpdate"
        is PolicyDecision.Warn -> "Warn"
    }

private const val MillisPerMinute = 60_000L
