package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChromeProvenanceCoverageFixtureTest {
    private val imageBytes = byteArrayOf(0x01, 0x02, 0x03)
    private val fixture = ChromeProvenanceCoverageFixture(imageBytes)

    @Test
    fun `runner characterizes requested mechanisms without product authority`() {
        val html = response("/web17").originalBytes.toString(Charsets.UTF_8)

        listOf(
            "NORMAL_IMG",
            "CSS_BACKGROUND",
            "SRCSET",
            "PICTURE",
            "EXTERNAL_SVG",
            "INLINE_SVG",
            "DATA_URL",
            "BLOB_URL",
            "CANVAS",
            "REPEAT_A",
            "REPEAT_B",
            "DYNAMIC_REPLACE",
        ).forEach { mechanism -> assertContains(html, mechanism) }
        assertContains(html, "GLOSH17_COMPLETE")
        assertContains(html, "/fixture-lease.js")
    }

    @Test
    fun `image and SVG counters are exact and report is bounded`() {
        assertEquals(imageBytes.toList(), response("/web17/media.png?id=one").originalBytes.toList())
        assertEquals("image/svg+xml", response("/web17/external.svg").contentType)
        val report =
            fixture.responseFor(
                ChromePhotosProxyRequest(
                    method = "POST",
                    target = "/web17/report",
                    body = "NORMAL_IMG:VISIBLE,CANVAS:VISIBLE".toByteArray(),
                ),
            )
        assertNotNull(report)

        assertEquals(
            "REPORT=NORMAL_IMG:VISIBLE,CANVAS:VISIBLE,IMAGE_REQ=1,SVG_REQ=1",
            fixture.state(),
        )
    }

    @Test
    fun `unknown path is not consumed by coverage fixture`() {
        assertEquals(null, fixture.responseFor(ChromePhotosProxyRequest("GET", "/web11b")))
    }

    private fun response(path: String): ChromePhotosFixtureResponse =
        assertNotNull(fixture.responseFor(ChromePhotosProxyRequest("GET", path)))
}
