package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagTabPreviewPolicyTest {
    @Test
    fun `capture requires a visible approved page in an open session`() {
        assertTrue(canCapture())
        assertFalse(canCapture(viewVisible = false))
        assertFalse(canCapture(sessionOpen = false))
        assertFalse(canCapture(pageVisible = false))
        assertFalse(canCapture(eligibilityConfirmed = false))
        assertFalse(canCapture(restricted = true))
        assertFalse(canCapture(videoCovered = true))
        assertTrue(
            DagTabPreviewPolicy.canCapture(
                viewVisible = true,
                sessionOpen = true,
                pageVisible = true,
                eligibilityConfirmed = true,
                restricted = false,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `late result is rejected after tab navigates`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertFalse(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 4,
                currentRevision = 9,
                pageVisible = true,
                restricted = false,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `result cannot cross into another tab`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertFalse(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 5,
                currentRevision = 8,
                pageVisible = true,
                restricted = false,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `result is rejected if page stopped being visible`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertFalse(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 4,
                currentRevision = 8,
                pageVisible = false,
                restricted = false,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `matching result remains attached to its exact page revision`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertTrue(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 4,
                currentRevision = 8,
                pageVisible = true,
                restricted = false,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `late result is rejected when page becomes restricted`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertFalse(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 4,
                currentRevision = 8,
                pageVisible = true,
                restricted = true,
                videoCovered = false,
            ),
        )
    }

    @Test
    fun `thumbnail capture and late results stay closed while video coverage is active`() {
        val request = DagTabPreviewRequest(tabId = 4, revision = 8)

        assertFalse(canCapture(videoCovered = true))
        assertFalse(
            DagTabPreviewPolicy.acceptsResult(
                request = request,
                currentTabId = 4,
                currentRevision = 8,
                pageVisible = true,
                restricted = false,
                videoCovered = true,
            ),
        )
    }

    private fun canCapture(
        viewVisible: Boolean = true,
        sessionOpen: Boolean = true,
        pageVisible: Boolean = true,
        eligibilityConfirmed: Boolean = true,
        restricted: Boolean = false,
        videoCovered: Boolean = false,
    ) = DagTabPreviewPolicy.canCapture(
        viewVisible = viewVisible,
        sessionOpen = sessionOpen,
        pageVisible = pageVisible,
        eligibilityConfirmed = eligibilityConfirmed,
        restricted = restricted,
        videoCovered = videoCovered,
    )
}
