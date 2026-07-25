package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagV2RuntimeScriptTest {
    @Test
    fun `runtime is media directed and does not rewrite site sources styles or classes`() {
        assertTrue(DagV2DocumentStartScript.contains("MutationObserver"))
        assertEquals(1, "new MutationObserver".toRegex().findAll(DagV2DocumentStartScript).count())
        assertTrue(DagV2DocumentStartScript.contains("attributeFilter:['src','srcset','poster']"))
        assertTrue(DagV2DocumentStartScript.contains("img,picture,source,video,audio,canvas,iframe"))
        assertTrue(DagV2DocumentStartScript.contains("img-src https:"))
        assertTrue(DagV2DocumentStartScript.contains("background-image:none"))
        assertTrue(DagV2DocumentStartScript.contains("__dag2RuntimeInstalled"))
        assertFalse(DagV2DocumentStartScript.contains("getComputedStyle"))
        assertFalse(DagV2DocumentStartScript.contains("removeAttribute('src')"))
        assertFalse(DagV2DocumentStartScript.contains("removeAttribute('srcset')"))
        assertFalse(DagV2DocumentStartScript.contains(".className ="))
        assertFalse(DagV2DocumentStartScript.contains(".style."))
        assertFalse(DagV2DocumentStartScript.contains("history["))
        assertFalse(DagV2DocumentStartScript.contains("history.pushState ="))
        assertFalse(DagV2DocumentStartScript.contains("history.replaceState ="))
        assertFalse(DagV2DocumentStartScript.contains("fetch ="))
        assertFalse(DagV2DocumentStartScript.contains("serviceWorker.register"))
        assertFalse(DagV2DocumentStartScript.contains("unregister()"))
    }
}
