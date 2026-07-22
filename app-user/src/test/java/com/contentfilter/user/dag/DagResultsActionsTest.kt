package com.contentfilter.user.dag

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DagResultsActionsTest {
    @Test
    fun `searches allowed and uncertain queries but stops blocked intent`() {
        assertEquals(DagQueryAction.Search, dagQueryAction(DagClassification.Allowed))
        assertEquals(DagQueryAction.Search, dagQueryAction(DagClassification.Uncertain))
        assertEquals(DagQueryAction.Block, dagQueryAction(DagClassification.Blocked))
    }

    @Test
    fun `offers exactly one additional Brave page when provider reports more`() {
        assertTrue(dagCanLoadMoreResults(page = 0, providerHasMoreResults = true))
        assertFalse(dagCanLoadMoreResults(page = 0, providerHasMoreResults = false))
        assertFalse(dagCanLoadMoreResults(page = 1, providerHasMoreResults = true))
    }

    @Test
    fun `shares only valid HTTPS links`() {
        assertEquals("https://example.com/path", dagShareableUrl("https://example.com/path"))
        assertNull(dagShareableUrl("http://example.com"))
        assertNull(dagShareableUrl("https:///missing-host"))
        assertNull(dagShareableUrl("not a url"))
    }

    @Test
    fun `recognizes an equivalent pending DAG review only`() {
        val pending = reviewRequest(status = RequestStatus.PendingRemote, domain = "www.example.com")

        assertTrue(pending.isPendingDagReviewFor("example.com"))
        assertFalse(pending.copy(status = RequestStatus.Approved).isPendingDagReviewFor("example.com"))
        assertFalse(pending.isPendingDagReviewFor("another.example"))
        assertEquals("Pendiente de revisión", dagReviewStatusLabel(RequestStatus.PendingRemote))
    }

    private fun reviewRequest(
        status: RequestStatus,
        domain: String,
    ): AccessRequest {
        return AccessRequest(
            id = "request-id",
            requestType = AccessRequestType.DOMAIN_ACCESS,
            targetType = PolicyTargetType.Domain,
            target = domain,
            targetPackageName = null,
            targetDomain = domain,
            reason = "DAG",
            requestedMinutes = null,
            status = status,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = null,
            deviceId = "device-id",
        )
    }
}
