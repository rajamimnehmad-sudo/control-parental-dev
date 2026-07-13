package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.WebPolicyDecision
import com.contentfilter.core.domain.model.WebPolicyDecisionSource
import com.contentfilter.core.domain.model.WebPolicyOutcome

/**
 * Selects one complete decision without merging or modifying layer-owned data.
 */
class WebPolicyDecisionResolver {
    fun resolve(
        candidates: Iterable<WebPolicyDecision>,
        nowEpochMillis: Long,
    ): WebPolicyDecision? =
        candidates
            .asSequence()
            .filterNot { it.isExpired(nowEpochMillis) }
            .maxWithOrNull(DecisionComparator)

    private companion object {
        val DecisionComparator =
            compareBy<WebPolicyDecision> { it.source.priority }
                .thenBy { it.version }
                .thenBy { it.evaluatedAtEpochMillis }
                .thenBy { it.outcome.tieBreakPriority }

        val WebPolicyDecisionSource.priority: Int
            get() =
                when (this) {
                    WebPolicyDecisionSource.PlatformPolicy -> 600
                    WebPolicyDecisionSource.AdministratorRule -> 500
                    WebPolicyDecisionSource.TechnicalAllowlist -> 400
                    WebPolicyDecisionSource.SignedDomainList -> 300
                    WebPolicyDecisionSource.LocalDomainClassifier -> 200
                    WebPolicyDecisionSource.LocalSearchClassifier -> 100
                    WebPolicyDecisionSource.DefaultPolicy -> 0
                }

        val WebPolicyOutcome.tieBreakPriority: Int
            get() =
                when (this) {
                    WebPolicyOutcome.RequireReview -> 3
                    WebPolicyOutcome.Block -> 2
                    WebPolicyOutcome.Uncertain -> 1
                    WebPolicyOutcome.Allow -> 0
                }
    }
}
