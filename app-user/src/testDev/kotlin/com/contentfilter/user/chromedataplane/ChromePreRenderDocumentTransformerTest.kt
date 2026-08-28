package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChromePreRenderDocumentTransformerTest {
    private var randomCall = 0
    private val transformer =
        ChromePreRenderDocumentTransformer { size ->
            randomCall += 1
            ByteArray(size) { index -> (randomCall + index).toByte() }
        }

    @Test
    fun `compatible document is shielded after doctype and before original tokens`() {
        val result =
            transformer.transform(
                sourceBytes = Source.toByteArray(),
                sourceHeaders = entityHeaders(),
                profile = ChromePreRenderShieldProfile.Compatible,
            )
        val html = result.bytes.toString(Charsets.UTF_8)
        val csp = result.headers.firstValue("Content-Security-Policy").orEmpty()

        assertTrue(html.indexOf("<!doctype html>") < html.indexOf("glosh-h18-shield-style"))
        assertTrue(html.indexOf("glosh-h18-shield-style") < html.indexOf("/site.css"))
        assertTrue(html.indexOf("glosh-h18-ready-host") < html.indexOf("site-original"))
        assertTrue(csp.contains("script-src 'self' 'nonce-"))
        assertTrue(csp.contains("style-src 'self' 'nonce-"))
        assertTrue(csp.contains("img-src https:"))
        assertFalse(csp.contains("data:"))
        assertFalse(csp.contains("blob:"))
        assertEquals("no-store", result.headers.firstValue("Cache-Control"))
        assertFalse(result.headers.any { it.name.equals("ETag", ignoreCase = true) })
        assertFalse(result.headers.any { it.name.equals("Content-Length", ignoreCase = true) })
        assertNotEquals(result.nonceDigest, result.readyTokenDigest)
    }

    @Test
    fun `strict document keeps only nonce-bearing injected script and style`() {
        val result = transformer.transform(Source.toByteArray(), emptyList(), ChromePreRenderShieldProfile.Strict)
        val csp = result.headers.firstValue("Content-Security-Policy").orEmpty()

        assertTrue(csp.contains("script-src 'nonce-"))
        assertTrue(csp.contains("style-src 'nonce-"))
        assertFalse(csp.contains("script-src 'self'"))
        assertFalse(csp.contains("style-src 'self'"))
        assertTrue(result.bytes.toString(Charsets.UTF_8).contains("BOOT_READY:STRICT"))
    }

    @Test
    fun `unsafe or unbounded insertion prefix fails closed`() {
        assertFailsWith<RuntimeException> {
            transformer.transform(
                "<!doctype html><script>bad()</script><head></head>".toByteArray(),
                emptyList(),
                ChromePreRenderShieldProfile.Compatible,
            )
        }
        assertFailsWith<RuntimeException> {
            transformer.transform(
                "<!doctype html><html><body>no head</body></html>".toByteArray(),
                emptyList(),
                ChromePreRenderShieldProfile.Compatible,
            )
        }
    }

    private fun entityHeaders() =
        listOf(
            ChromeHttpHeader("Content-Type", "text/html; charset=utf-8"),
            ChromeHttpHeader("Content-Length", "123"),
            ChromeHttpHeader("ETag", "\"old\""),
            ChromeHttpHeader("Content-Security-Policy", "default-src 'self'"),
        )

    private companion object {
        const val Source =
            "<!doctype html><html><head><link rel=\"stylesheet\" href=\"/site.css\"></head>" +
                "<body id=\"site-original\">normal</body></html>"
    }
}
