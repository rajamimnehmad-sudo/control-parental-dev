package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ChromeVisualShieldFixtureTest {
    @Test
    fun `sentinel is explicit signed DOM content with deterministic red and black pixels`() {
        val html = body(ChromeVisualShieldFixture.SentinelPath)

        assertContains(html, ChromeVisualShieldLabControl.FixtureSignature)
        assertContains(html, "data-region-id=\"${ChromeVisualShieldLabControl.RegionId}\"")
        assertContains(html, "rgb(220,20,48) 0 50%")
        assertContains(html, "rgb(0,0,0) 50% 100%")
        assertContains(html, "id=\"shield-sentinel\"")
    }

    @Test
    fun `control page contains no sentinel element`() {
        val html = body(ChromeVisualShieldFixture.ControlPath)

        assertFalse(html.contains("id=\"shield-sentinel\""))
        assertContains(html, "data-stage=\"control\"")
    }

    @Test
    fun `delayed page creates sentinel only from deterministic local timer`() {
        val html = body(ChromeVisualShieldFixture.DelayedPath)

        assertContains(html, "setTimeout")
        assertContains(html, "1200")
        assertContains(html, "document.createElement('div')")
        assertFalse(html.contains("fetch("))
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
    }

    @Test
    fun `fixture ignores paths outside its namespace`() {
        val request = request("/web11b")
        assertEquals(null, ChromeVisualShieldFixture.responseFor(request))
    }

    private fun body(path: String): String {
        val response = assertNotNull(ChromeVisualShieldFixture.responseFor(request(path)))
        return response.originalBytes.toString(Charsets.UTF_8)
    }

    private fun request(path: String) =
        ChromePhotosProxyRequest(
            method = "GET",
            target = path,
            version = "HTTP/1.1",
            headers = emptyList(),
            body = ByteArray(0),
        )
}
