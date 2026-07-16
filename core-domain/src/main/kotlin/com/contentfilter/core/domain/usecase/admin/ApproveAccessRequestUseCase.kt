package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.math.max

class ApproveAccessRequestUseCase(
    private val requestRepository: AccessRequestRepository,
    private val policyRepository: PolicyRepository,
    private val dailyLimitRepository: DailyLimitRepository,
) {
    suspend operator fun invoke(request: AccessRequest) {
        val policy = policyRepository.getActivePolicy(request.deviceId)
        require(request.requestType != AccessRequestType.DOMAIN_ACCESS || policy.rules.dagEnabled()) {
            "No se puede aprobar un sitio DAG sin la política completa y habilitada del dispositivo."
        }
        request.allowTarget()?.let { target ->
            policy.rules
                .filter {
                    it.enabled &&
                        it.scope == RuleScope.App &&
                        it.target == target &&
                        it.action == RuleAction.Block
                }
                .forEach { policyRepository.saveRule(it.copy(enabled = false), request.deviceId) }
            if (request.requestType == AccessRequestType.APP_ACCESS) {
                dailyLimitRepository.deleteAppLimitsForTarget(
                    target = target,
                    deviceId = request.deviceId,
                )
            }
        }
        request.toAllowRule()?.let { generatedRule ->
            val existingRule =
                policy.rules
                    .filter {
                        it.scope == generatedRule.scope &&
                            it.target == generatedRule.target &&
                            it.action == RuleAction.Allow
                    }.maxByOrNull(PolicyRule::priority)
            val rule =
                existingRule?.copy(
                    priority = max(existingRule.priority, APPROVED_REQUEST_PRIORITY),
                    enabled = true,
                ) ?: generatedRule
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

private suspend fun DailyLimitRepository.deleteAppLimitsForTarget(
    target: String,
    deviceId: String?,
) {
    observeLimits(deviceId)
        .first()
        .filter { limit ->
            limit.enabled &&
                limit.targetType == PolicyTargetType.App &&
                limit.target == target
        }
        .forEach { deleteLimit(it) }
}
