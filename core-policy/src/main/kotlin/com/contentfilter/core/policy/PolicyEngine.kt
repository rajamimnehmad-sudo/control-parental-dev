package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.AppPolicyContext
import com.contentfilter.core.domain.model.DomainPolicyContext
import com.contentfilter.core.domain.model.PolicyContext
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicySnapshot

/**
 * Single decision point for business policy.
 */
interface PolicyEngine {
    fun evaluateApp(
        snapshot: PolicySnapshot,
        context: AppPolicyContext,
    ): PolicyDecision

    fun evaluateDomain(
        snapshot: PolicySnapshot,
        context: DomainPolicyContext,
    ): PolicyDecision

    fun evaluate(
        snapshot: PolicySnapshot,
        context: PolicyContext,
    ): PolicyDecision
}
