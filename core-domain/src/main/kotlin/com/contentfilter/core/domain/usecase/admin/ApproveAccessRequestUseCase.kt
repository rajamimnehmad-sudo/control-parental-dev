package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import java.util.UUID

class ApproveAccessRequestUseCase(
    private val requestRepository: AccessRequestRepository,
    private val policyRepository: PolicyRepository,
) {
    suspend operator fun invoke(request: AccessRequest) {
        request.toAllowRule()?.let { rule ->
            policyRepository.saveRule(rule)
        }
        requestRepository.updateStatus(request.id, RequestStatus.Approved)
    }

    private fun AccessRequest.toAllowRule(): PolicyRule? {
        val scope = when (requestType) {
            AccessRequestType.APP_ACCESS -> RuleScope.App
            AccessRequestType.DOMAIN_ACCESS -> RuleScope.Domain
            else -> return null
        }
        val allowTarget = when (scope) {
            RuleScope.App -> targetPackageName ?: target.takeIf { targetType == PolicyTargetType.App }
            RuleScope.Domain -> targetDomain ?: target.takeIf { targetType == PolicyTargetType.Domain }
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return PolicyRule(
            id = UUID.randomUUID().toString(),
            level = PolicyLevel.Account,
            scope = scope,
            target = allowTarget,
            action = RuleAction.Allow,
            priority = ApprovedRequestPriority,
            enabled = true,
        )
    }

    private companion object {
        const val ApprovedRequestPriority = 1_000
    }
}
