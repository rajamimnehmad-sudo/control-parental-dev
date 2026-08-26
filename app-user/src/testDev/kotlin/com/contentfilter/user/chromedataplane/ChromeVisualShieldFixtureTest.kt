package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertContains(html, "Math.min(canvas.width / sourceWidth, canvas.height / sourceHeight)")
        assertContains(html, "context.fillStyle = '${ChromeVisualShieldContainContract.NeutralBackground}'")
        assertContains(html, "context.drawImage(bitmap, drawX, drawY, drawWidth, drawHeight)")
        assertContains(html, "renderContract=${ChromeVisualShieldContainContract.Version}")
        assertFalse(html.contains("object-fit: fill"))
        assertFalse(html.contains("drawImage(bitmap, 0, 0)"))
        assertEquals("application/json; charset=utf-8", payload.contentType)
        assertFalse(payload.contentType.startsWith("image/"))
        assertContains(payload.originalBytes.toString(Charsets.UTF_8), "\"ready\":false")
    }

    @Test
    fun `contain geometry preserves landscape and portrait aspect ratios without cropping`() {
        val landscape = assertNotNull(ChromeVisualShieldContainContract.geometry(1600, 900, 700, 660))
        val portrait = assertNotNull(ChromeVisualShieldContainContract.geometry(900, 1600, 700, 660))

        assertEquals(700.0, landscape.width)
        assertEquals(393.75, landscape.height)
        assertEquals(0.0, landscape.left)
        assertTrue(landscape.top > 0.0)
        assertEquals(371.25, portrait.width)
        assertEquals(660.0, portrait.height)
        assertTrue(portrait.left > 0.0)
        assertEquals(0.0, portrait.top)
        assertEquals(1600.0 / 900.0, landscape.width / landscape.height, 0.000_001)
        assertEquals(900.0 / 1600.0, portrait.width / portrait.height, 0.000_001)
    }

    @Test
    fun `all seventeen historical candidates use generic canvas and JSON payload routes`() {
        assertEquals(17, ChromeVisualShieldFixtureSample.renderedMatrix.size)
        ChromeVisualShieldFixtureSample.renderedMatrix.forEach { sample ->
            val html = body(ChromeVisualShieldFixture.canvasPath(sample))
            val payload =
                assertNotNull(
                    ChromeVisualShieldFixture.responseFor(
                        request(ChromeVisualShieldFixture.payloadPath(sample)),
                    ),
                )
            assertContains(html, "data-sample=\"${sample.wireName}\"")
            assertContains(html, sample.expectedSha256)
            assertEquals("application/json; charset=utf-8", payload.contentType)
            assertFalse(payload.contentType.startsWith("image/"))
        }
    }

    @Test
    fun `render probe command exists only behind dev package and dump receiver contract`() {
        assertTrue(ChromeVisualShieldLabReceiverContract.accepts("com.contentfilter.user.dev"))
        assertFalse(ChromeVisualShieldLabReceiverContract.accepts("com.contentfilter.user"))
        assertEquals("android.permission.DUMP", ChromeVisualShieldLabReceiverContract.RequiredManifestPermission)
        assertEquals(
            "com.contentfilter.user.chromevisualshield.command.RENDER_PROBE",
            ChromeVisualShieldLabReceiver.ActionRenderProbe,
        )
    }

    @Test
    fun `real fixture samples pin canonical historical provenance`() {
        assertEquals("https://httpbingo.org/image/png", ChromeVisualShieldFixtureSample.Safe.sourceUrl)
        assertEquals(8_090, ChromeVisualShieldFixtureSample.Safe.expectedBytes)
        assertEquals(
            "541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1",
            ChromeVisualShieldFixtureSample.Safe.expectedSha256,
        )
        assertEquals(
            "https://farm6.staticflickr.com/3200/2970012318_98f7c80583_o.jpg",
            ChromeVisualShieldFixtureSample.Block.sourceUrl,
        )
        assertEquals(146_249, ChromeVisualShieldFixtureSample.Block.expectedBytes)
        assertEquals(
            "9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94",
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
