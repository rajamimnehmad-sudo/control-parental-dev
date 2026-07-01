package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.repository.ActivationRepository

class ActivateDeviceUseCase(
    private val repository: ActivationRepository,
) {
    suspend operator fun invoke(credentials: ActivationCredentials) = repository.activate(credentials)
}
