package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.map

class ObservePolicyRulesUseCase(
    private val repository: PolicyRepository,
) {
    operator fun invoke() = repository.observeActivePolicy().map { it.rules }
}
