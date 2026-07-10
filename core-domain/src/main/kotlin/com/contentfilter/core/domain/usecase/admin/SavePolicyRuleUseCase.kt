package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.repository.PolicyRepository

class SavePolicyRuleUseCase(
    private val repository: PolicyRepository,
) {
    suspend operator fun invoke(
        rule: PolicyRule,
        deviceId: String? = null,
    ) {
        repository.saveRule(rule, deviceId)
    }

    suspend fun saveAll(
        rules: List<PolicyRule>,
        deviceId: String? = null,
        requestId: String? = null,
    ): PolicyMutationReceipt = repository.saveRules(rules, deviceId, requestId)
}
