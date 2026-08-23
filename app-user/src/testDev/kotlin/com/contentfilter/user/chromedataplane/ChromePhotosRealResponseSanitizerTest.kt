package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosRealResponseSanitizerTest {
    private val safe = "safe-public-image".toByteArray()
    private val blocked = "block-public-image".toByteArray()
    private val placeholder = "neutral-png-placeholder".toByteArray()
    private val allowlist = ChromePhotosHostAllowlist(ChromePhotosRealWebLabConfig.allowedHosts)
    private val transformer =
        ChromePhotosResourceTransformer(
            safeBytes = emptyList(),
            blockedBytes = emptyList(),
            placeholderBytes = placeholder,
            safeContentHashes = setOf(sha256(safe)),
            blockedContentHashes = setOf(sha256(blocked)),
        )
    private val sanitizer = ChromePhotosRealResponseSanitizer(transformer, allowlist, placeholder)

    @Test
    fun `SAFE image remains byte identical with real MIME`() {
        val result = sanitizer.sanitize("GET", upstream("image/png", safe))

        assertEquals(ChromePhotosResourceDecision.Safe, result.decision)
        assertEquals("image/png", result.contentType)
        assertContentEquals(safe, result.bytes)
    }

    @Test
    fun `BLOCK and UNKNOWN images become PNG placeholder without original bytes`() {
        val blockedResult = sanitizer.sanitize("GET", upstream("image/webp", blocked))
        val unknownResult = sanitizer.sanitize("GET", upstream("image/jpeg", "unknown".toByteArray()))

        listOf(blockedResult, unknownResult).forEach { result ->
            assertEquals("image/png", result.contentType)
            assertContentEquals(placeholder, result.bytes)
        }
        assertEquals(ChromePhotosResourceDecision.Block, blockedResult.decision)
        assertEquals(ChromePhotosResourceDecision.Unknown, unknownResult.decision)
        assertFalse(blockedResult.bytes.contentEquals(blocked))
    }

    @Test
    fun `oversized or encoded image fails closed to placeholder`() {
        val tooLarge = sanitizer.sanitize("GET", upstream("image/avif", ByteArray(0), tooLarge = true))
        val compressed = sanitizer.sanitize("GET", upstream("image/jpeg", safe, encoding = "gzip"))

        assertEquals(ChromePhotosResourceDecision.Unknown, tooLarge.decision)
        assertEquals(ChromePhotosResourceDecision.Unknown, compressed.decision)
        assertContentEquals(placeholder, tooLarge.bytes)
        assertContentEquals(placeholder, compressed.bytes)
    }

    @Test
    fun `non image bytes pass through and HEAD never fabricates a body`() {
        val html = "<html>public</html>".toByteArray()
        val get = sanitizer.sanitize("GET", upstream("text/html; charset=utf-8", html))
        val head = sanitizer.sanitize("HEAD", upstream("image/png", ByteArray(0)))

        assertEquals(ChromePhotosResourceDecision.Passthrough, get.decision)
        assertContentEquals(html, get.bytes)
        assertEquals(0, head.bytes.size)
        assertEquals("image/png", head.contentType)
    }

    @Test
    fun `redirect passes only exact allowlisted HTTPS destination`() {
        val allowed =
            sanitizer.sanitize(
                "GET",
                redirect("https://${ChromePhotosRealWebLabConfig.GitHubRawHost}/public/image.avif"),
            )
        val relative = sanitizer.sanitize("GET", redirect("/image/png"))
        val disallowed = sanitizer.sanitize("GET", redirect("https://example.com/private"))
        val downgrade = sanitizer.sanitize("GET", redirect("http://${ChromePhotosRealWebLabConfig.HttpBingoHost}/"))

        assertEquals(302, allowed.statusCode)
        assertTrue(allowed.location!!.startsWith("https://"))
        assertEquals("/image/png", relative.location)
        assertEquals(502, disallowed.statusCode)
        assertNull(disallowed.location)
        assertEquals(502, downgrade.statusCode)
    }

    @Test
    fun `upstream request policy cannot forward credentials conditionals or ranges`() {
        val names = ChromePhotosUpstreamRequestPolicy.headers.keys.mapTo(mutableSetOf()) { it.lowercase() }

        assertTrue(names.intersect(ChromePhotosUpstreamRequestPolicy.strippedRequestHeaders).isEmpty())
        assertEquals("identity", ChromePhotosUpstreamRequestPolicy.headers["Accept-Encoding"])
        assertEquals("no-cache", ChromePhotosUpstreamRequestPolicy.headers["Cache-Control"])
    }

    @Test
    fun `sanitized response rebuilds entity headers and strips validators and encodings`() {
        val response = sanitizer.sanitize("GET", upstream("image/webp", blocked))
        val headers = ChromePhotosClientResponseHeaderPolicy.headersFor(response, response.bytes.size)
        val normalizedNames = headers.keys.mapTo(mutableSetOf()) { it.lowercase() }

        assertTrue(normalizedNames.intersect(ChromePhotosClientResponseHeaderPolicy.invalidatedEntityHeaders).isEmpty())
        assertEquals("image/png", headers["Content-Type"])
        assertEquals(placeholder.size.toString(), headers["Content-Length"])
        assertEquals("no-store", headers["Cache-Control"])
    }

    private fun upstream(
        contentType: String,
        bytes: ByteArray,
        tooLarge: Boolean = false,
        encoding: String? = null,
    ) = ChromePhotosUpstreamResponse(
        host = ChromePhotosRealWebLabConfig.HttpBingoHost,
        statusCode = 200,
        statusText = "OK",
        contentType = contentType,
        contentEncoding = encoding,
        location = null,
        body = bytes,
        bodyTooLarge = tooLarge,
        protocol = "h2",
    )

    private fun redirect(location: String) =
        ChromePhotosUpstreamResponse(
            host = ChromePhotosRealWebLabConfig.GitHubHost,
            statusCode = 302,
            statusText = "Found",
            contentType = "text/html",
            contentEncoding = null,
            location = location,
            body = ByteArray(0),
            bodyTooLarge = false,
            protocol = "h2",
        )
}
