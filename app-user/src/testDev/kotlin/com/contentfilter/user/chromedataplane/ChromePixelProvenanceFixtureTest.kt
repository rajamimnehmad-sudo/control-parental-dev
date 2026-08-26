package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePixelProvenanceFixtureTest {
    private val fixture = ChromePixelProvenanceFixture()

    @Test
    fun `matrix explicitly separates renderer network and browser storage sources`() {
        assertEquals(
            setOf(
                ChromePixelProvenanceSource.RendererLocal,
                ChromePixelProvenanceSource.NetworkCarrier,
                ChromePixelProvenanceSource.BrowserStorage,
            ),
            ChromePixelProvenanceVector.entries.mapTo(linkedSetOf()) { it.source },
        )
        assertEquals(10, ChromePixelProvenanceVector.entries.size)
        assertEquals(
            setOf("JAVASCRIPT", "JSON", "WASM"),
            ChromePixelProvenanceVector.entries
                .filter { it.source == ChromePixelProvenanceSource.NetworkCarrier }
                .mapTo(linkedSetOf()) { it.reportKey },
        )
    }

    @Test
    fun `runner redirects to service worker compatible scope`() {
        val response = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a"))!!

        assertEquals(308, response.statusCode)
        assertEquals("/web13a/", response.headers.firstValue("Location"))
    }

    @Test
    fun `runner publishes every provenance vector and controlled report endpoint`() {
        val response = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/"))!!
        val html = response.originalBytes.toString(Charsets.UTF_8)

        assertEquals("text/html; charset=utf-8", response.contentType)
        ChromePixelProvenanceVector.entries.forEach { vector -> assertTrue(html.contains(vector.reportKey)) }
        assertTrue(html.contains("data:image/png;base64"))
        assertTrue(html.contains("URL.createObjectURL"))
        assertTrue(html.contains("getContext('2d')"))
        assertTrue(html.contains("getContext('webgl')"))
        assertTrue(html.contains("WebAssembly.instantiateStreaming"))
        assertTrue(html.contains("instance.exports.red"))
        assertTrue(html.contains("navigator.serviceWorker.register"))
        assertTrue(html.contains("registration.unregister"))
        assertTrue(html.contains("caches.delete"))
        assertTrue(html.contains("/web13a/report"))
        assertTrue(html.contains("/web13a/state"))
        assertTrue(html.contains("GLOSH13A_COMPLETE"))
    }

    @Test
    fun `inline SVG rendered requires browser rasterization and known pixel samples`() {
        val response = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/"))!!
        val html = response.originalBytes.toString(Charsets.UTF_8)

        assertFalse(html.contains("getBoundingClientRect"))
        assertTrue(html.contains("new XMLSerializer().serializeToString(svg)"))
        assertTrue(html.contains("context.drawImage(image,0,0,320,180)"))
        assertTrue(html.contains("getImageData(60,90,1,1)"))
        assertTrue(html.contains("getImageData(20,90,1,1)"))
        assertTrue(html.contains("pixelMatches(redPixel,220,20,48)"))
        assertTrue(html.contains("pixelMatches(blackPixel,0,0,0)"))
    }

    @Test
    fun `storage cleanup verifies registration and cache postconditions`() {
        val response = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/"))!!
        val html = response.originalBytes.toString(Charsets.UTF_8)

        assertTrue(html.contains("remainingRegistrations"))
        assertTrue(html.contains("registrationAbsent"))
        assertTrue(html.contains("caches.has('glosh-13a-v1')"))
        assertTrue(html.contains("cacheAbsent"))
        assertTrue(html.contains("registrationAbsent&&cacheAbsent&&clean"))
    }

    @Test
    fun `network carrier endpoints are typed and deterministic`() {
        val script = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/external.js"))!!
        val json = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/instructions.json"))!!
        val wasm = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/control.wasm"))!!
        val worker = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/sw.js"))!!

        assertEquals("application/javascript; charset=utf-8", script.contentType)
        assertEquals("application/json; charset=utf-8", json.contentType)
        assertEquals("application/wasm", wasm.contentType)
        assertContentEquals(
            byteArrayOf(
                0x00,
                0x61,
                0x73,
                0x6d,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x05,
                0x01,
                0x60,
                0x00,
                0x01,
                0x7f,
                0x03,
                0x02,
                0x01,
                0x00,
                0x07,
                0x07,
                0x01,
                0x03,
                0x72,
                0x65,
                0x64,
                0x00,
                0x00,
                0x0a,
                0x07,
                0x01,
                0x05,
                0x00,
                0x41,
                0xdc.toByte(),
                0x01,
                0x0b,
            ),
            wasm.originalBytes,
        )
        assertEquals("/web13a/", worker.headers.firstValue("Service-Worker-Allowed"))
        val workerScript = worker.originalBytes.toString(Charsets.UTF_8)
        assertTrue(workerScript.contains("glosh-13a-v1"))
        assertTrue(workerScript.contains("caches.open"))
        assertTrue(workerScript.contains("respondWith"))
        assertTrue(workerScript.contains("/web13a/sw-synthetic.png"))
        assertTrue(workerScript.contains("/web13a/cache-only.png"))
    }

    @Test
    fun `state distinguishes proxied carriers from synthetic origin fallbacks`() {
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/external.js"))
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/instructions.json"))
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/control.wasm"))
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/sw.js"))
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/sw-synthetic.png"))
        fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/cache-only.png"))
        fixture.responseFor(
            ChromePhotosProxyRequest(
                method = "POST",
                target = "/web13a/report",
                body = "DATA_URL:RENDERED,CANVAS_2D:RENDERED".toByteArray(),
            ),
        )

        val state = fixture.report()
        assertTrue(state.contains("PAGE=DATA_URL:RENDERED,CANVAS_2D:RENDERED"))
        assertTrue(state.contains("JS_REQ=1"))
        assertTrue(state.contains("JSON_REQ=1"))
        assertTrue(state.contains("WASM_REQ=1"))
        assertTrue(state.contains("SW_SCRIPT_REQ=1"))
        assertTrue(state.contains("SW_ORIGIN_FALLBACK=1"))
        assertTrue(state.contains("CACHE_ORIGIN_FALLBACK=1"))
    }

    @Test
    fun `report rejects method misuse and unsafe body`() {
        val wrongMethod = fixture.responseFor(ChromePhotosProxyRequest("GET", "/web13a/report"))!!
        val unsafe =
            fixture.responseFor(
                ChromePhotosProxyRequest(
                    method = "POST",
                    target = "/web13a/report",
                    body = "DATA_URL:<secret>".toByteArray(),
                ),
            )!!

        assertEquals(405, wrongMethod.statusCode)
        assertEquals(200, unsafe.statusCode)
        assertTrue(fixture.report().startsWith("PAGE=invalid"))
    }
}
