package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeStockPreRenderShieldFixtureTest {
    private val fixture =
        ChromeStockPreRenderShieldFixture(
            networkSentinelBytes = "raw-network-sentinel".toByteArray(),
            auditPlaceholderBytes = "audit-placeholder".toByteArray(),
            transformer = ChromePreRenderDocumentTransformer { size -> ByteArray(size) { 7 } },
        )

    @Test
    fun `static attack is exactly ordinary DOM and CSS after parser-first bootstrap`() {
        val source = fixture.sourceDocument(ChromePreRenderShieldProfile.Compatible, dynamic = false)
        val css = fixture.siteCss()
        val response = response("/web18/compatible-static")
        val html = response.originalBytes.toString(Charsets.UTF_8)

        assertEquals(64, Regex("class=\"pixel b[01]\"").findAll(source).count())
        assertFalse(source.contains("<script"))
        assertTrue(html.indexOf("glosh-h18-shield-style") < html.indexOf("/web18/site.css"))
        listOf("url(", "gradient", "paint(", "canvas", "<svg", "data:", "blob:").forEach { token ->
            assertFalse(css.contains(token, ignoreCase = true), token)
        }
        assertTrue(css.contains("background-color:#dc1430"))
        assertTrue(css.contains("background-color:#000000"))
    }

    @Test
    fun `dynamic attack uses only normal DOM class state and fetch`() {
        val script = fixture.siteScript()

        assertTrue(script.contains("raster.dataset.live='true'"))
        assertTrue(script.contains("fetch('/web18/data.json'"))
        listOf("canvas", "svg", "createimagebitmap", "createobjecturl", "blob:", "data:", "worker").forEach { token ->
            assertFalse(script.contains(token, ignoreCase = true), token)
        }
    }

    @Test
    fun `strict profile blocks original script and stylesheet sources but bootstrap remains nonce authorized`() {
        val response = response("/web18/strict")
        val csp = response.headers.firstValue("Content-Security-Policy").orEmpty()

        assertTrue(csp.contains("script-src 'nonce-"))
        assertTrue(csp.contains("style-src 'nonce-"))
        assertFalse(csp.contains("script-src 'self'"))
        assertFalse(csp.contains("style-src 'self'"))
        assertTrue(response.originalBytes.toString(Charsets.UTF_8).contains("BOOT_READY:STRICT"))
    }

    @Test
    fun `network control and bounded reports expose hashes without retaining bodies`() {
        assertContentEquals("raw-network-sentinel".toByteArray(), response("/web18/raw-sentinel.png").originalBytes)
        fixture.responseFor(
            ChromePhotosProxyRequest(
                method = "POST",
                target = "/web18/report",
                body = "SITE_JS_NORMAL_DYNAMIC_CSS".toByteArray(),
            ),
        )
        val state = response("/web18/state").originalBytes.toString(Charsets.US_ASCII)

        assertTrue(state.contains("SITE_JS_NORMAL_DYNAMIC_CSS"))
        assertTrue(state.contains("NETWORK_SHA=${sha256("raw-network-sentinel".toByteArray())}"))
        assertTrue(state.contains("AUDIT_SHA=${sha256("audit-placeholder".toByteArray())}"))
        assertFalse(state.contains("raw-network-sentinel"))
    }

    private fun response(path: String): ChromePhotosFixtureResponse =
        requireNotNull(fixture.responseFor(ChromePhotosProxyRequest("GET", path)))
}
