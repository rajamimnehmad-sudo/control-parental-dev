package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeMediaShieldDocumentTransformerTest {
    private var randomCall = 0
    private val transformer =
        ChromeMediaShieldDocumentTransformer(Session, PolicyEpoch) { size ->
            randomCall += 1
            ByteArray(size) { index -> (randomCall + index).toByte() }
        }

    init {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
    }

    @AfterTest
    fun tearDown() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun `doctype and site CSP survive while parser-first shield receives exact nonce`() {
        val result =
            assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                transformer.transform(
                    sourceBytes = Source.toByteArray(),
                    sourceHeaders =
                        listOf(
                            ChromeHttpHeader("Content-Type", "text/html; charset=utf-8"),
                            ChromeHttpHeader("Content-Length", "999"),
                            ChromeHttpHeader("ETag", "old"),
                            ChromeHttpHeader("Content-Security-Policy", "default-src 'self'; object-src 'none'"),
                        ),
                    disposition = transformDisposition(),
                ),
            ).document
        val html = result.bytes.toString(Charsets.UTF_8)
        val policies = result.headers.filter { it.name.equals("Content-Security-Policy", true) }.map { it.value }

        assertTrue(html.indexOf("<!doctype html>") < html.indexOf(ChromeMediaShieldBootstrap.CurtainStyleElementId))
        assertTrue(
            html.indexOf(ChromeMediaShieldBootstrap.CurtainStyleElementId) <
                html.indexOf(ChromeMediaShieldBootstrap.StyleElementId),
        )
        assertTrue(html.indexOf(ChromeMediaShieldBootstrap.StyleElementId) < html.indexOf("site-original"))
        assertTrue(html.indexOf("glosh-shield-ready:") < html.indexOf("site-original"))
        assertEquals(2, policies.size)
        assertTrue(policies.first().contains("default-src 'self'"))
        assertTrue(policies.first().contains("script-src 'self' 'nonce-"))
        assertTrue(policies.first().contains("style-src 'self' 'nonce-"))
        assertTrue(policies.last().contains("img-src https: http:"))
        assertFalse(policies.any { it.contains("unsafe-inline") || it.contains("unsafe-eval") })
        assertFalse(result.headers.any { it.name.equals("Content-Length", true) })
        assertFalse(result.headers.any { it.name.equals("ETag", true) })
        assertEquals("no-store", result.headers.firstValue("Cache-Control"))
        assertEquals(result.identity, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().currentTopLevel)
    }

    @Test
    fun `implicit head is created before original visual tokens`() {
        val source = "<!doctype html><html><body><img src=\"https://example.test/a.png\"></body></html>"
        val result =
            assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                transformer.transform(source.toByteArray(), emptyList(), transformDisposition()),
            ).document.bytes.toString(Charsets.UTF_8)

        assertContains(result, "<head><style id=\"${ChromeMediaShieldBootstrap.CurtainStyleElementId}\"")
        assertTrue(result.indexOf(ChromeMediaShieldBootstrap.StyleElementId) < result.indexOf("<body>"))
    }

    @Test
    fun `declarative shadow and static iframe are structurally neutralized without authorizing iframe`() {
        val source =
            "<!doctype html><html><head></head><body>" +
                "<template shadowrootmode=\"closed\"><canvas></canvas></template>" +
                "<iframe src=\"https://example.test/frame\"></iframe></body></html>"
        val result =
            assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                transformer.transform(
                    source.toByteArray(),
                    emptyList(),
                    transformDisposition(ChromeMediaShieldDocumentKind.Subdocument),
                ),
            ).document
        val html = result.bytes.toString(Charsets.UTF_8)

        assertContains(html, "data-glosh-blocked-shadowrootmode=")
        assertContains(html, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
        assertContains(html, "TOP_LEVEL=false")
        assertFalse(html.contains("<style id=\"${ChromeMediaShieldBootstrap.CurtainStyleElementId}\""))
        assertFalse(result.identity.topLevel)
        assertEquals(null, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().currentTopLevel)
    }

    @Test
    fun `unsafe prefix and oversized input fail closed without ready authority`() {
        val unsafe =
            transformer.transform(
                "<!doctype html><script>bad()</script><html><head></head></html>".toByteArray(),
                emptyList(),
                transformDisposition(),
            )
        val oversized =
            transformer.transform(
                ByteArray(2 * 1024 * 1024 + 1) { 'a'.code.toByte() },
                emptyList(),
                transformDisposition(),
            )

        assertIs<ChromeMediaShieldDocumentResult.FailClosed>(unsafe)
        assertIs<ChromeMediaShieldDocumentResult.FailClosed>(oversized)
        assertEquals(null, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().currentTopLevel)
        assertEquals(0L, transformer.metrics().outstanding)
    }

    @Test
    fun `malformed suffix fails closed instead of bypassing structural media neutralization`() {
        val result =
            transformer.transform(
                "<!doctype html><html><head></head><body><div title='unterminated>".toByteArray(),
                emptyList(),
                transformDisposition(),
            )

        assertIs<ChromeMediaShieldDocumentResult.FailClosed>(result)
        assertEquals(null, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().currentTopLevel)
    }

    private fun transformDisposition(kind: ChromeMediaShieldDocumentKind = ChromeMediaShieldDocumentKind.TopLevel) =
        ChromeMediaShieldDocumentDisposition.Transform(kind, "utf-8")

    private companion object {
        const val Session = "h19-session"
        const val PolicyEpoch = 19L
        const val Source =
            "<!doctype html><html><head></head><body id=\"site-original\">normal</body></html>"
    }
}
