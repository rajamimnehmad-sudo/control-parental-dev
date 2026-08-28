package com.contentfilter.user.chromedataplane

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosRealResponseSanitizerTest {
    private val safe = png("safe-public-image")
    private val blocked = webp("block-public-image")
    private val placeholder = "neutral-png-placeholder".toByteArray()
    private val authority =
        ChromePublicDestinationAuthority(
            ChromeHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
        )
    private val transformer =
        ChromePhotosResourceTransformer(
            safeBytes = emptyList(),
            blockedBytes = emptyList(),
            placeholderBytes = placeholder,
            safeContentHashes = setOf(sha256(safe)),
            blockedContentHashes = setOf(sha256(blocked)),
        )
    private val sanitizer =
        ChromePhotosRealResponseSanitizer(
            transformer,
            authority,
            placeholder,
            maximumImageBytes = 64,
        )

    @Test
    fun `SAFE image remains byte identical while transformed entity headers are coherent`() {
        val result = sanitizer.sanitize("GET", upstream("image/png", safe, extraHeaders = entityHeaders()))

        assertEquals(ChromePhotosResourceDecision.Safe, result.decision)
        assertEquals("image/png", result.contentType)
        assertContentEquals(safe, result.bytes)
        assertNull(result.headers.firstValue("Content-Encoding"))
        assertNull(result.headers.firstValue("ETag"))
        assertEquals("no-store", result.headers.firstValue("Cache-Control"))
    }

    @Test
    fun `BLOCK and UNKNOWN images become placeholder without original bytes`() {
        val blockedResult = sanitizer.sanitize("GET", upstream("image/webp", blocked))
        val unknownResult = sanitizer.sanitize("GET", upstream("image/jpeg", jpeg("unknown")))

        listOf(blockedResult, unknownResult).forEach { result ->
            assertEquals("image/png", result.contentType)
            assertContentEquals(placeholder, result.bytes)
            assertEquals(200, result.statusCode)
        }
        assertEquals(ChromePhotosResourceDecision.Block, blockedResult.decision)
        assertEquals(ChromePhotosResourceDecision.Unknown, unknownResult.decision)
        assertFalse(blockedResult.bytes.contentEquals(blocked))
    }

    @Test
    fun `oversized or encoded image fails closed before original delivery`() {
        val tooLarge = sanitizer.sanitize("GET", upstream("image/avif", avif() + ByteArray(65)))
        val compressed = sanitizer.sanitize("GET", upstream("image/jpeg", safe, encoding = "gzip"))
        val brotli = sanitizer.sanitize("GET", upstream("image/jpeg", safe, encoding = "br"))
        val unknownMime = sanitizer.sanitize("GET", upstream(null, safe, encoding = "gzip"))

        listOf(tooLarge, compressed, brotli, unknownMime).forEach { result ->
            assertEquals(ChromePhotosResourceDecision.Unknown, result.decision)
            assertContentEquals(placeholder, result.bytes)
            assertFalse(result.bytes.contentEquals(safe))
        }
    }

    @Test
    fun `HEAD has no body while image 304 becomes current generation placeholder`() {
        val head =
            sanitizer.sanitize(
                "HEAD",
                upstream("image/png", ByteArray(0), extraHeaders = entityHeaders()),
            )
        val notModified =
            sanitizer.sanitize(
                "GET",
                upstream(
                    "image/png",
                    ByteArray(0),
                    statusCode = 304,
                    extraHeaders = entityHeaders(),
                ),
            )

        assertEquals(0, head.bytes.size)
        assertEquals("\"fixture\"", head.headers.firstValue("ETag"))
        assertContentEquals(placeholder, notModified.bytes)
        assertEquals(200, notModified.statusCode)
        assertEquals(ChromePhotosResourceDecision.Unknown, notModified.decision)
    }

    @Test
    fun `mislabeled identity image is inspected and SAFE uses canonical MIME`() {
        val result = sanitizer.sanitize("GET", upstream("application/octet-stream", safe))

        assertEquals(ChromePhotosResourceDecision.Safe, result.decision)
        assertEquals("image/png", result.contentType)
        assertContentEquals(safe, result.bytes)
        assertEquals("no-store", result.headers.firstValue("Cache-Control"))
        assertEquals("nosniff", result.headers.firstValue("X-Content-Type-Options"))
    }

    @Test
    fun `declared image HTML and partial image never reach Chrome raw`() {
        val html = "<html>not-image</html>".toByteArray()
        val declared = sanitizer.sanitize("GET", upstream("image/png", html))
        val partial = sanitizer.sanitize("GET", upstream("image/png", safe, statusCode = 206))

        listOf(declared, partial).forEach { result ->
            assertEquals(ChromePhotosResourceDecision.Unknown, result.decision)
            assertContentEquals(placeholder, result.bytes)
            assertEquals(200, result.statusCode)
        }
        assertEquals(sha256(html), declared.observedBodyDigest)
        assertNull(partial.observedBodyDigest)
    }

    @Test
    fun `SVG animated and malformed candidates never deliver source bytes`() {
        val sources =
            listOf(
                "<svg><script>alert(1)</script></svg>".toByteArray(),
                "GIF89a-animated".toByteArray(),
                png("acTL-animation"),
                "not-an-image".toByteArray(),
            )

        sources.forEach { source ->
            val result = sanitizer.sanitize("GET", upstream("image/png", source))
            assertEquals(ChromePhotosResourceDecision.Unknown, result.decision)
            assertContentEquals(placeholder, result.bytes)
            assertFalse(result.bytes.contentEquals(source))
            assertEquals("nosniff", result.headers.firstValue("X-Content-Type-Options"))
        }
    }

    @Test
    fun `redirect preserves real status and permits dynamic HTTPS cross host only`() {
        val allowed = sanitizer.sanitizeRedirect(redirect("https://new-public.example/path", 308))
        val relative = sanitizer.sanitizeRedirect(redirect("/next", 307))
        val downgrade = sanitizer.sanitizeRedirect(redirect("http://example.com/", 302))

        assertEquals(308, allowed.statusCode)
        assertEquals("https://new-public.example/path", allowed.location)
        assertEquals("/next", relative.location)
        assertEquals(502, downgrade.statusCode)
        assertNull(downgrade.location)
    }

    @Test
    fun `hop by hop headers are removed but cookies and security headers remain ordered`() {
        val headers =
            listOf(
                ChromeHttpHeader("Connection", "keep-alive, X-Private-Hop"),
                ChromeHttpHeader("X-Private-Hop", "drop"),
                ChromeHttpHeader("Transfer-Encoding", "chunked"),
                ChromeHttpHeader("Set-Cookie", "a=1; Secure; HttpOnly"),
                ChromeHttpHeader("Set-Cookie", "b=2; SameSite=Lax"),
                ChromeHttpHeader("Content-Security-Policy", "default-src 'self'"),
                ChromeHttpHeader("Access-Control-Allow-Origin", "https://example.com"),
            )
        val filtered = ChromeHttpHeaderPolicy.downstreamResponseHeaders(headers)

        assertEquals(2, filtered.count { it.name.equals("Set-Cookie", true) })
        assertTrue(filtered.any { it.name == "Content-Security-Policy" })
        assertTrue(filtered.any { it.name == "Access-Control-Allow-Origin" })
        assertFalse(filtered.any { it.name.equals("Connection", true) || it.name == "X-Private-Hop" })
    }

    private fun upstream(
        contentType: String?,
        bytes: ByteArray,
        encoding: String? = null,
        statusCode: Int = 200,
        extraHeaders: List<ChromeHttpHeader> = emptyList(),
    ) = ChromePhotosUpstreamResponse(
        host = "example.com",
        statusCode = statusCode,
        statusText = if (statusCode == 304) "Not Modified" else "OK",
        headers =
            listOfNotNull(
                contentType?.let { ChromeHttpHeader("Content-Type", it) },
                encoding?.let { ChromeHttpHeader("Content-Encoding", it) },
            ) + extraHeaders,
        body = bytes.inputStream(),
        bodyLength = bytes.size.toLong(),
        protocol = "h2",
    )

    private fun redirect(
        location: String,
        statusCode: Int,
    ) = ChromePhotosUpstreamResponse(
        host = "source.example",
        statusCode = statusCode,
        statusText = "Redirect",
        headers =
            listOf(
                ChromeHttpHeader("Location", location),
                ChromeHttpHeader("Cache-Control", "max-age=10"),
            ),
        body = ByteArray(0).inputStream(),
        bodyLength = 0,
        protocol = "h2",
    )

    private fun entityHeaders() =
        listOf(
            ChromeHttpHeader("Content-Encoding", "identity"),
            ChromeHttpHeader("ETag", "\"fixture\""),
            ChromeHttpHeader("Last-Modified", "Sun, 24 Aug 2026 12:00:00 GMT"),
        )

    private fun png(value: String) =
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) + value.toByteArray()

    private fun jpeg(value: String) = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + value.toByteArray()

    private fun webp(value: String): ByteArray {
        val header = ByteArray(12)
        "RIFF".toByteArray().copyInto(header, 0)
        "WEBP".toByteArray().copyInto(header, 8)
        return header + value.toByteArray()
    }

    private fun avif(): ByteArray {
        val bytes = ByteArray(16)
        bytes[3] = bytes.size.toByte()
        "ftyp".toByteArray().copyInto(bytes, 4)
        "avif".toByteArray().copyInto(bytes, 8)
        return bytes
    }
}
