package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl
import java.util.Base64
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

    @Test
    fun `real sample carrier is JSON base64 reconstructed into verified canvas`() {
        val html = body(ChromeVisualShieldFixture.SafeCanvasPath)
        val payload =
            assertNotNull(
                ChromeVisualShieldFixture.responseFor(
                    request(ChromeVisualShieldFixture.SafePayloadPath),
                ),
            )

        assertContains(html, ChromeVisualShieldFixtureSample.Safe.expectedSha256)
        assertContains(html, "crypto.subtle.digest('SHA-256', bytes)")
        assertContains(html, "createImageBitmap(new Blob([bytes], { type: 'application/octet-stream' }))")
        assertContains(html, "<canvas id=\"fixture-canvas\"")
        assertContains(html, "carrierVisible=canvas")
        assertEquals("application/json; charset=utf-8", payload.contentType)
        assertFalse(payload.contentType.startsWith("image/"))
        assertContains(payload.originalBytes.toString(Charsets.UTF_8), "\"ready\":false")
    }

    @Test
    fun `real fixture samples pin canonical historical provenance`() {
        assertEquals("https://httpbingo.org/image/png", ChromeVisualShieldFixtureSample.Safe.sourceUrl)
        assertEquals(8_090, ChromeVisualShieldFixtureSample.Safe.expectedBytes)
        assertEquals(
            "541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1",
            ChromeVisualShieldFixtureSample.Safe.expectedSha256,
        )
        assertEquals("https://www.gstatic.com/webp/gallery/1.webp", ChromeVisualShieldFixtureSample.Block.sourceUrl)
        assertEquals(30_320, ChromeVisualShieldFixtureSample.Block.expectedBytes)
        assertEquals(
            "4a5afeaff8483923da964bc7896f02d0283e8bff99b5b8f82a31ae3214dab1d0",
            ChromeVisualShieldFixtureSample.Block.expectedSha256,
        )
    }

    @Test
    fun `fixture loader rejects bytes that cannot be the historical sample`() {
        val sample = ChromeVisualShieldFixtureSample.Block
        ChromeVisualShieldFixtureSampleStore.reset(sample)
        ChromeVisualShieldFixtureSampleStore.append(
            sample,
            Base64.getEncoder().encodeToString("not-the-historical-sample".toByteArray()),
        )

        val result = ChromeVisualShieldFixtureSampleStore.commit(sample)

        assertContains(result, "result=fixture_size_mismatch")
        assertContains(result, "sample=block")
        ChromeVisualShieldFixtureSampleStore.clear()
    }

    @Test
    fun `fixture loader rejects oversized chunks before staging growth`() {
        val sample = ChromeVisualShieldFixtureSample.Block
        ChromeVisualShieldFixtureSampleStore.reset(sample)

        val result = ChromeVisualShieldFixtureSampleStore.append(sample, "A".repeat(24_580))

        assertContains(result, "result=fixture_chunk_too_large")
        assertContains(result, "sample=block")
        ChromeVisualShieldFixtureSampleStore.clear()
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
