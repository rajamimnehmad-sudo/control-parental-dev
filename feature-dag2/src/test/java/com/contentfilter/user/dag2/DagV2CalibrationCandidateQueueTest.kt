package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagV2CalibrationCandidateQueueTest {
    private val session = DagV2DocumentSession().start("https://shop.example/products")
    private val request =
        DagV2ResourceRequest(
            url = "https://images.example/photo.jpg?size=large#fragment",
            headers = emptyMap(),
            isForMainFrame = false,
            source = DagV2ResourceSource.WebView,
            documentContext = session.requestContext,
            attribution = DagV2RequestAttribution.Current,
        )

    @Test
    fun `disabled calibration never records candidates`() {
        val queue = DagV2CalibrationCandidateQueue()
        queue.onDocument(session)

        assertEquals(
            DagV2CalibrationQueueResult.Disabled,
            queue.offer(DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.RasterImage)),
        )
        assertEquals(emptyList(), queue.snapshot())
    }

    @Test
    fun `current candidate is queued once by normalized resource`() {
        val queue = enabledQueue()
        val first = DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.RasterImage)
        val second = DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.RasterImage)

        assertEquals(DagV2CalibrationQueueResult.Queued, queue.offer(first))
        assertEquals(DagV2CalibrationQueueResult.Deduplicated, queue.offer(second))
        assertEquals(1, queue.snapshot().size)
    }

    @Test
    fun `old generation candidate is discarded after navigation`() {
        val queue = enabledQueue()
        val old = DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.RasterImage)
        val next = DagV2DocumentSession().start("https://shop.example/category")
        queue.onDocument(next)

        assertEquals(DagV2CalibrationQueueResult.Stale, queue.offer(old))
        assertEquals(emptyList(), queue.snapshot())
    }

    @Test
    fun `svg and unattributed resources are never reviewable`() {
        val queue = enabledQueue()
        val svg = DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.SvgImage)
        val unattributed =
            DagV2CalibrationCandidate.from(
                request.copy(attribution = DagV2RequestAttribution.Unattributed),
                session,
                DagV2ResourceKind.RasterImage,
            )

        assertEquals(DagV2CalibrationQueueResult.Stale, queue.offer(svg))
        assertEquals(DagV2CalibrationQueueResult.Stale, queue.offer(unattributed))
    }

    @Test
    fun `disabling clears sensitive in-memory URLs`() {
        val queue = enabledQueue()
        val candidate = DagV2CalibrationCandidate.from(request, session, DagV2ResourceKind.RasterImage)
        queue.offer(candidate)

        queue.setEnabled(false)

        assertEquals(emptyList(), queue.snapshot())
        assertNull(queue.candidate(candidate.candidateId))
    }

    private fun enabledQueue(): DagV2CalibrationCandidateQueue =
        DagV2CalibrationCandidateQueue().apply {
            setEnabled(true)
            onDocument(session)
        }
}
