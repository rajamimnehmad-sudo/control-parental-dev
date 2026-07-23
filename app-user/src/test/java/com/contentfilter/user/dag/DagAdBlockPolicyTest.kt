package com.contentfilter.user.dag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DagAdBlockPolicyTest {
    @Test
    fun `blocks known advertising subresources`() {
        assertTrue(
            dagShouldBlockAdRequest(
                "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
                isMainFrame = false,
            ),
        )
    }

    @Test
    fun `does not block main frames or normal commerce resources`() {
        assertFalse(dagShouldBlockAdRequest("https://doubleclick.net/", isMainFrame = true))
        assertFalse(dagShouldBlockAdRequest("https://www.fravega.com/static/app.js", isMainFrame = false))
    }
}
