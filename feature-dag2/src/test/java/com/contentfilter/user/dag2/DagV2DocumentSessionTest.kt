package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagV2DocumentSessionTest {
    @Test
    fun `one document creates one analysis and internal interactions do not restart it`() {
        val sessions = DagV2DocumentSession()
        val initial = sessions.start("https://example.com/products")

        val analyzing = sessions.beginFullAnalysis(initial.sessionId, initial.navigationToken)
        assertNotNull(analyzing)
        assertEquals(1, analyzing.fullPageAnalysisCount)
        assertNull(sessions.beginFullAnalysis(initial.sessionId, initial.navigationToken))

        DagV2InternalInteraction.entries.forEach(sessions::recordInternalInteraction)
        val afterInteractions = sessions.snapshot()
        assertNotNull(afterInteractions)
        assertEquals(initial.sessionId, afterInteractions.sessionId)
        assertEquals(1, afterInteractions.fullPageAnalysisCount)

        val completed = sessions.completeFullAnalysis(initial.sessionId, initial.navigationToken)
        assertNotNull(completed)
        assertTrue(completed.fullAnalysisCompleted)
        assertEquals(1, completed.fullPageAnalysisCount)
    }

    @Test
    fun `new document invalidates the former navigation token`() {
        val sessions = DagV2DocumentSession()
        val first = sessions.start("https://example.com/a")
        val cancelled = sessions.cancelActive()
        val second = sessions.start("https://example.com/b")

        assertNotNull(cancelled)
        assertTrue(cancelled.cancelled)
        assertNotEquals(first.sessionId, second.sessionId)
        assertNotEquals(first.navigationToken, second.navigationToken)
        assertFalse(sessions.isCurrent(first.sessionId, first.navigationToken))
        assertTrue(sessions.isCurrent(second.sessionId, second.navigationToken))
        assertNull(sessions.completeFullAnalysis(first.sessionId, first.navigationToken))
    }

    @Test
    fun `cancelled session cannot complete or modify the new session`() {
        val sessions = DagV2DocumentSession()
        val first = sessions.start("https://example.com/a")
        sessions.beginFullAnalysis(first.sessionId, first.navigationToken)
        sessions.cancelActive()
        val second = sessions.start("https://example.com/b")
        val secondAnalysis = sessions.beginFullAnalysis(second.sessionId, second.navigationToken)

        assertNull(sessions.completeFullAnalysis(first.sessionId, first.navigationToken))
        assertNotNull(secondAnalysis)
        assertEquals(1, sessions.snapshot()?.fullPageAnalysisCount)
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
    }
}
