package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagLoadTransitionPolicyTest {
    @Test
    fun `visible top level navigation is covered before Gecko clears the page`() {
        assertTrue(
            DagLoadTransitionPolicy.shouldCover(
                currentUrl = "https://example.com/one",
                targetUrl = "https://example.com/two",
                targetsCurrentWindow = true,
                pageVisible = true,
                barrierAlreadyWaiting = false,
            ),
        )
    }

    @Test
    fun `fragment navigation does not close an already safe document`() {
        assertFalse(
            DagLoadTransitionPolicy.shouldCover(
                currentUrl = "https://example.com/page#one",
                targetUrl = "https://example.com/page#two",
                targetsCurrentWindow = true,
                pageVisible = true,
                barrierAlreadyWaiting = false,
            ),
        )
    }

    @Test
    fun `reload target is recognized as the same protected document`() {
        assertTrue(
            DagLoadTransitionPolicy.targetsSameDocument(
                currentUrl = "https://example.com/page#old",
                targetUrl = "https://example.com/page#new",
            ),
        )
        assertFalse(
            DagLoadTransitionPolicy.targetsSameDocument(
                currentUrl = "https://example.com/page?version=1",
                targetUrl = "https://example.com/page?version=2",
            ),
        )
    }

    @Test
    fun `new windows and an existing barrier are not covered twice`() {
        assertFalse(
            DagLoadTransitionPolicy.shouldCover(
                currentUrl = "https://example.com/one",
                targetUrl = "https://example.com/two",
                targetsCurrentWindow = false,
                pageVisible = true,
                barrierAlreadyWaiting = false,
            ),
        )
        assertFalse(
            DagLoadTransitionPolicy.shouldCover(
                currentUrl = "https://example.com/one",
                targetUrl = "https://example.com/two",
                targetsCurrentWindow = true,
                pageVisible = true,
                barrierAlreadyWaiting = true,
            ),
        )
    }
}
