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
        assertFalse(DagV2DocumentStartScript.contains("background-image:none"))
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

    @Test
    fun `navigation api and bounded polling fallback detect spa routes without rewriting history`() {
        assertTrue(DagV2DocumentStartScript.contains("window.navigation.addEventListener('navigate'"))
        assertTrue(DagV2DocumentStartScript.contains("setInterval(function() { dag2ReportRoute('poll'); }, 500)"))
        assertTrue(DagV2DocumentStartScript.contains("clearInterval(dag2RouteTimer)"))
        assertTrue(DagV2DocumentStartScript.contains("addEventListener('hashchange'"))
        assertTrue(DagV2DocumentStartScript.contains("addEventListener('popstate'"))
        assertEquals(1, "setInterval\\(".toRegex().findAll(DagV2DocumentStartScript).count())
        assertFalse(DagV2DocumentStartScript.contains("history.pushState"))
        assertFalse(DagV2DocumentStartScript.contains("history.replaceState"))
    }

    @Test
    fun `runtime bridge carries immutable document generation`() {
        val session = DagV2DocumentSession().start("https://example.com/a")
        val script = dagV2DocumentStartScript(session.requestContext)

        assertTrue(script.contains(session.sessionId))
        assertTrue(script.contains(session.navigationToken))
        assertTrue(script.contains("window.top !== window.self"))
        assertTrue(script.contains("Object.freeze"))
    }
}
