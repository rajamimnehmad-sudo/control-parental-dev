package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagV2RuntimeScriptTest {
    @Test
    fun `runtime is media directed and does not rewrite site sources styles or classes`() {
        assertTrue(DagV2DocumentStartScript.contains("MutationObserver"))
        assertTrue(DagV2DocumentStartScript.contains("attributeFilter:['src','srcset','poster']"))
        assertFalse(DagV2DocumentStartScript.contains("getComputedStyle"))
        assertFalse(DagV2DocumentStartScript.contains("removeAttribute('src')"))
        assertFalse(DagV2DocumentStartScript.contains("removeAttribute('srcset')"))
        assertFalse(DagV2DocumentStartScript.contains(".className ="))
        assertFalse(DagV2DocumentStartScript.contains(".style."))
        assertFalse(DagV2DocumentStartScript.contains("serviceWorker.register"))
        assertFalse(DagV2DocumentStartScript.contains("unregister()"))
    }
}
