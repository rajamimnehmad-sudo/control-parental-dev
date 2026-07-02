package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import java.util.UUID

class GrantExtraTimeUseCase(
    private val requestRepository: AccessRequestRepository,
    private val grantRepository: ExtraTimeGrantRepository,
) {
    suspend operator fun invoke(
        request: AccessRequest,
        minutes: Int,
        nowEpochMillis: Long,
    ) {
        grantRepository.saveGrant(
            ExtraTimeGrant(
                id = UUID.randomUUID().toString(),
                requestId = request.id,
                targetType = request.targetType,
                target = request.target,
                grantedMinutes = minutes,
                validUntilEpochMillis = nowEpochMillis + minutes * MILLIS_PER_MINUTE,
            ),
        )
        requestRepository.updateStatus(request.id, RequestStatus.Approved)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
