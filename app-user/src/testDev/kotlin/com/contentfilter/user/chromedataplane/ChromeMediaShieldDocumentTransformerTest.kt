package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.util.Base64
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
        val scriptTags = Regex("<script nonce=\\\"([^\\\"]+)\\\"[^>]*>").findAll(html).toList()

        assertTrue(html.indexOf("<!doctype html>") < html.indexOf(ChromeMediaShieldBootstrap.CurtainStyleElementId))
        assertTrue(
            html.indexOf(ChromeMediaShieldBootstrap.CurtainStyleElementId) <
                html.indexOf(ChromeMediaShieldBootstrap.StyleElementId),
        )
        assertTrue(html.indexOf(ChromeMediaShieldBootstrap.StyleElementId) < html.indexOf("site-original"))
        val failClosedInstaller = html.indexOf(ChromeMediaShieldBootstrap.ParserBarrierFailClosedName)
        val prelude = html.indexOf("const READY='")
        val barrier = html.indexOf(ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl)
        val guard = html.indexOf(ChromeMediaShieldBootstrap.ParserBarrierGuardName, barrier)
        assertTrue(failClosedInstaller in 0 until prelude)
        assertTrue(prelude in 0 until barrier)
        assertTrue(barrier in 0 until guard)
        assertTrue(guard in 0 until html.indexOf("site-original"))
        assertFalse(
            html.substring(barrier, html.indexOf("</script>", barrier)).contains("__GLOSH_READY_TOKEN__"),
        )
        assertContains(html, "referrerpolicy=\"no-referrer\"")
        assertFalse(html.contains("glosh-shield-ready:"))
        assertEquals(4, scriptTags.size)
        assertEquals(1, scriptTags.map { it.groupValues[1] }.distinct().size)
        val exactScriptNonce = scriptTags.first().groupValues[1]
        assertTrue(policies.first().contains("'nonce-$exactScriptNonce'"))
        assertContains(
            html,
            "src=\"${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}\" referrerpolicy=\"no-referrer\"",
        )
        assertFalse(
            html.contains("${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}?") ||
                html.contains("${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}#"),
        )
        assertEquals(2, policies.size)
        assertTrue(policies.first().contains("default-src 'self'"))
        assertTrue(policies.first().contains("script-src 'self' 'nonce-"))
        assertTrue(policies.first().contains("style-src 'self' 'nonce-"))
        assertTrue(policies.last().contains("img-src https: http:"))
        assertFalse(policies.any { it.contains("unsafe-inline") || it.contains("unsafe-eval") })
        assertFalse(result.headers.any { it.name.equals("Content-Length", true) })
        assertFalse(result.headers.any { it.name.equals("ETag", true) })
        assertEquals("no-store", result.headers.firstValue("Cache-Control"))
        val expectedScript = ChromeMediaShieldBootstrap.script(token(3), token(2), topLevel = true)
        val expectedBarrier =
            "<script nonce=\"${token(1)}\" src=\"${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}\" " +
                "referrerpolicy=\"no-referrer\"></script><script nonce=\"${token(1)}\">" +
                "${ChromeMediaShieldBootstrap.parserBarrierGuardScript()}</script>"
        val completeBootstrap =
            ChromeMediaShieldBootstrap.parserBarrierFailClosedInstallerScript() + expectedScript + expectedBarrier
        assertEquals(
            sha256(completeBootstrap.toByteArray(Charsets.US_ASCII)),
            result.bootstrapSha256,
        )
        assertFalse(
            result.bootstrapSha256 == sha256((expectedScript + expectedBarrier).toByteArray(Charsets.US_ASCII)),
        )
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
    fun `H20 top-level and subdocument own parser-first curtains and no external release tail`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, 20L)
        var call = 0
        val selfShieldTransformer =
            ChromeMediaShieldDocumentTransformer(
                sessionId = Session,
                policyEpoch = 20L,
                documentSelfShieldEnabled = true,
                randomBytes = { size -> ByteArray(size) { index -> (++call + index).toByte() } },
            )

        val top =
            assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                selfShieldTransformer.transform(Source.toByteArray(), emptyList(), transformDisposition()),
            ).document.bytes.toString(Charsets.UTF_8)
        val frame =
            assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                selfShieldTransformer.transform(
                    Source.toByteArray(),
                    emptyList(),
                    transformDisposition(ChromeMediaShieldDocumentKind.Subdocument),
                ),
            ).document.bytes.toString(Charsets.UTF_8)

        for (html in listOf(top, frame)) {
            assertContains(html, "<style id=\"${ChromeMediaShieldBootstrap.CurtainStyleElementId}\"")
            assertContains(html, "SELF_SHIELD=true")
            assertContains(html, ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyUrl)
            assertFalse(html.contains("src=\"${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}\""))
            assertTrue(html.indexOf("SELF_SHIELD=true") < html.indexOf("site-original"))
        }
        assertContains(top, "TOP_LEVEL=true")
        assertContains(frame, "TOP_LEVEL=false")
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
                    listOf(
                        ChromeHttpHeader("Content-Security-Policy", "default-src 'none'; script-src 'none'"),
                    ),
                    transformDisposition(ChromeMediaShieldDocumentKind.Subdocument),
                ),
            ).document
        val html = result.bytes.toString(Charsets.UTF_8)
        val policies = result.headers.filter { it.name.equals("Content-Security-Policy", true) }.map { it.value }
        val scripts = Regex("<script nonce=\\\"([^\\\"]+)\\\"[^>]*>").findAll(html).toList()

        assertContains(html, "data-glosh-blocked-shadowrootmode=")
        assertContains(html, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
        assertContains(html, "TOP_LEVEL=false")
        assertFalse(html.contains("src=\"${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}\""))
        assertFalse(html.contains("<style id=\"${ChromeMediaShieldBootstrap.CurtainStyleElementId}\""))
        assertTrue(
            html.indexOf(ChromeMediaShieldBootstrap.ParserBarrierFailClosedName) < html.indexOf("TOP_LEVEL=false"),
        )
        assertTrue(html.indexOf("TOP_LEVEL=false") < html.indexOf(ChromeMediaShieldBootstrap.SubdocumentGuardName))
        assertEquals(3, scripts.size)
        assertEquals(1, scripts.map { it.groupValues[1] }.distinct().size)
        assertTrue(policies.first().contains("script-src 'nonce-${scripts.first().groupValues[1]}'"))
        val expectedScript = ChromeMediaShieldBootstrap.script(token(3), token(2), topLevel = false)
        val expectedTail =
            "<script nonce=\"${token(1)}\">${ChromeMediaShieldBootstrap.subdocumentGuardScript()}</script>"
        assertEquals(
            sha256(
                (ChromeMediaShieldBootstrap.parserBarrierFailClosedInstallerScript() + expectedScript + expectedTail)
                    .toByteArray(Charsets.US_ASCII),
            ),
            result.bootstrapSha256,
        )
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

    private fun token(call: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { index -> (call + index).toByte() })

    private companion object {
        const val Session = "h19-session"
        const val PolicyEpoch = 19L
        const val Source =
            "<!doctype html><html><head></head><body id=\"site-original\">normal</body></html>"
    }
}
