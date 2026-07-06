package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GrantExtraTimeUseCaseTest {
    @Test
    fun `extra time approval creates grant and approves request`() =
        runBlocking {
            val requestRepository = FakeAccessRequestRepository()
            val grantRepository = FakeExtraTimeGrantRepository()
            val useCase = GrantExtraTimeUseCase(requestRepository, grantRepository)

            useCase(
                request = extraTimeRequest(),
                minutes = 15,
                nowEpochMillis = 1_000L,
            )

            val grant = grantRepository.savedGrant
            assertEquals("request-1", grant?.requestId)
            assertEquals(PolicyTargetType.App, grant?.targetType)
            assertEquals(AirbnbPackage, grant?.target)
            assertEquals(15, grant?.grantedMinutes)
            assertEquals(901_000L, grant?.validUntilEpochMillis)
            assertEquals(RequestStatus.Approved, requestRepository.updatedStatus)
        }

    private fun extraTimeRequest(): AccessRequest =
        AccessRequest(
            id = "request-1",
            requestType = AccessRequestType.EXTRA_TIME,
            targetType = PolicyTargetType.App,
            target = AirbnbPackage,
            targetPackageName = AirbnbPackage,
            targetDomain = null,
            reason = "",
            requestedMinutes = 15,
            status = RequestStatus.PendingRemote,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = null,
            deviceId = "device-1",
        )

    private class FakeAccessRequestRepository : AccessRequestRepository {
        var updatedStatus: RequestStatus? = null

        override fun observeRequests(): Flow<List<AccessRequest>> = flowOf(emptyList())

        override fun observePendingRequests(): Flow<List<AccessRequest>> = flowOf(emptyList())

        override suspend fun saveRequest(request: AccessRequest) = Unit

        override suspend fun updateStatus(
            requestId: String,
            status: RequestStatus,
        ) {
            updatedStatus = status
        }
    }

    private class FakeExtraTimeGrantRepository : ExtraTimeGrantRepository {
        var savedGrant: ExtraTimeGrant? = null

        override fun observeActiveGrants(nowEpochMillis: Long): Flow<List<ExtraTimeGrant>> = flowOf(emptyList())

        override fun observeGrants(): Flow<List<ExtraTimeGrant>> = flowOf(emptyList())

        override suspend fun saveGrant(grant: ExtraTimeGrant) {
            savedGrant = grant
        }
    }

    private companion object {
        const val AirbnbPackage = "com.airbnb.android"
    }
}
