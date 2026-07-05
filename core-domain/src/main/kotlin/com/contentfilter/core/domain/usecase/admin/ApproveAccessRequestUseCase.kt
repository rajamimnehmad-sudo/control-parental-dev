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
        request.allowTarget()?.let { target ->
            policyRepository.getActivePolicy(request.deviceId).rules
                .filter {
                    it.enabled &&
                        it.scope == RuleScope.App &&
                        it.target == target &&
                        it.action == RuleAction.Block
                }
                .forEach { policyRepository.saveRule(it.copy(enabled = false), request.deviceId) }
        }
        request.toAllowRule()?.let { rule ->
            policyRepository.saveRule(rule, request.deviceId)
        }
        requestRepository.updateStatus(request.id, RequestStatus.Approved)
    }

    private fun AccessRequest.toAllowRule(): PolicyRule? {
        val scope =
            when (requestType) {
                AccessRequestType.APP_ACCESS -> RuleScope.App
                AccessRequestType.DOMAIN_ACCESS -> RuleScope.Domain
                else -> return null
            }
        val allowTarget = allowTarget(scope) ?: return null

        return PolicyRule(
            id = UUID.randomUUID().toString(),
            level = PolicyLevel.Account,
            scope = scope,
            target = allowTarget,
            action = RuleAction.Allow,
            priority = APPROVED_REQUEST_PRIORITY,
            enabled = true,
        )
    }

    private fun AccessRequest.allowTarget(scope: RuleScope = RuleScope.App): String? =
        when (scope) {
            RuleScope.App -> targetPackageName ?: target.takeIf { targetType == PolicyTargetType.App }
            RuleScope.Domain -> targetDomain ?: target.takeIf { targetType == PolicyTargetType.Domain }
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val APPROVED_REQUEST_PRIORITY = 1_000
    }
}
