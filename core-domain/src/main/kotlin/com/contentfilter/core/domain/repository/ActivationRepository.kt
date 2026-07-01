package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult

interface ActivationRepository {
    suspend fun activate(credentials: ActivationCredentials): ActivationResult
}
