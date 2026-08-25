package com.contentfilter.user.chromedataplane

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeImageContentAuthorityTest {
    private val authority = ChromeImageContentAuthority()

    @Test
    fun `image intent normalizes encoding range and validators without dropping credentials`() {
        val request =
            request(
                ChromeHttpHeader("Sec-Fetch-Dest", "image"),
                ChromeHttpHeader("Accept-Encoding", "gzip, br"),
                ChromeHttpHeader("Range", "bytes=0-99"),
                ChromeHttpHeader("If-Range", "\"old\""),
                ChromeHttpHeader("If-None-Match", "\"cached\""),
                ChromeHttpHeader("If-Modified-Since", "Sun, 24 Aug 2026 12:00:00 GMT"),
                ChromeHttpHeader("Cookie", "fixture=value"),
                ChromeHttpHeader("Authorization", "Bearer fixture"),
            )

        val normalized = authority.normalizeUpstreamRequest(request)

        assertEquals(listOf("identity"), normalized.headerValues("Accept-Encoding"))
        assertTrue(normalized.headerValues("Range").isEmpty())
        assertTrue(normalized.headerValues("If-Range").isEmpty())
        assertTrue(normalized.headerValues("If-None-Match").isEmpty())
        assertTrue(normalized.headerValues("If-Modified-Since").isEmpty())
        assertEquals("fixture=value", normalized.firstHeader("Cookie"))
        assertEquals("Bearer fixture", normalized.firstHeader("Authorization"))
    }

    @Test
    fun `normal web request preserves compression range and validators`() {
        val request =
            request(
                ChromeHttpHeader("Sec-Fetch-Dest", "document"),
                ChromeHttpHeader("Accept-Encoding", "gzip, br"),
                ChromeHttpHeader("Range", "bytes=10-20"),
                ChromeHttpHeader("If-None-Match", "\"v1\""),
            )

        assertEquals(request, authority.normalizeUpstreamRequest(request))
    }

    @Test
    fun `magic recognizes supported and fail closed image containers`() {
        assertEquals(ChromeImageFormat.Jpeg, authority.sniffFormat(jpeg("body")))
        assertEquals(ChromeImageFormat.Png, authority.sniffFormat(png("body")))
        assertEquals(ChromeImageFormat.Webp, authority.sniffFormat(webp()))
        assertEquals(ChromeImageFormat.Avif, authority.sniffFormat(avif("avif")))
        assertEquals(ChromeImageFormat.Gif, authority.sniffFormat("GIF89a-body".toByteArray()))
        assertEquals(ChromeImageFormat.Bmp, authority.sniffFormat("BM-body".toByteArray()))
        assertEquals(ChromeImageFormat.Ico, authority.sniffFormat(byteArrayOf(0, 0, 1, 0, 1)))
        assertEquals(ChromeImageFormat.Heif, authority.sniffFormat(avif("heic")))
        assertEquals(ChromeImageFormat.Svg, authority.sniffFormat(" <svg></svg>".toByteArray()))
        assertNull(authority.sniffFormat("<html>not image</html>".toByteArray()))
    }

    @Test
    fun `explicit non image MIME streams pass through without consuming a prefix`() {
        listOf(
            "text/event-stream" to "data: event\n\n",
            "text/html" to "<html><img></html>",
            "application/json" to "{\"value\":true}",
            "text/css" to "body { color: black; }",
            "application/javascript" to "window.fixture = true;",
        ).forEach { (contentType, body) ->
            val stream = ReadGuardInputStream(body.toByteArray(), maximumReads = 0)
            val inspection = authority.inspect(request(), response(stream, body.length.toLong(), contentType))

            assertIs<ChromeImageContentInspection.Passthrough>(inspection)
            assertEquals(0, stream.readCalls, contentType)
        }
    }

    @Test
    fun `SVG authority requires svg to be the document root`() {
        val xhtml = "<?xml version=\"1.0\"?><html><body><svg/></body></html>".toByteArray()
        val feed = "<?xml version=\"1.0\"?><feed><svg/></feed>".toByteArray()
        val svg =
            ("\uFEFF <?xml version=\"1.0\"?>" +
                "<!--fixture--><!DOCTYPE svg [<!ELEMENT svg ANY>]><svg viewBox=\"0 0 1 1\"/>").toByteArray()

        assertNull(authority.sniffFormat(xhtml))
        assertNull(authority.sniffFormat(feed))
        assertEquals(ChromeImageFormat.Svg, authority.sniffFormat(svg))
    }

    @Test
    fun `fragmented absent MIME JPEG is detected with only signature reads`() {
        val bytes = jpeg("fragmented")
        val stream = ReadGuardInputStream(bytes, maximumReads = 3)

        val candidate =
            assertIs<ChromeImageContentInspection.Candidate>(
                authority.inspect(request(), response(stream, bytes.size.toLong(), null)),
            )

        assertEquals(ChromeImageFormat.Jpeg, candidate.prefixFormat)
        assertEquals(3, stream.readCalls)
        assertContentEquals(bytes, candidate.response.body.readBytes())
    }

    @Test
    fun `ambiguous clear non image stops early instead of filling sniff limit`() {
        val bytes = "plain incremental stream that remains open".toByteArray()
        val stream = ReadGuardInputStream(bytes, maximumReads = 5)

        val passthrough =
            assertIs<ChromeImageContentInspection.Passthrough>(
                authority.inspect(request(), response(stream, -1, null)),
            )

        assertEquals(5, stream.readCalls)
        assertContentEquals(bytes, passthrough.response.body.readBytes())
    }

    @Test
    fun `fragmented HTML root stops progressive sniff before later inline svg`() {
        val bytes = "<html><body><svg/></body></html>".toByteArray()
        val stream = ReadGuardInputStream(bytes, maximumReads = 6)

        val passthrough =
            assertIs<ChromeImageContentInspection.Passthrough>(
                authority.inspect(request(), response(stream, -1, "application/octet-stream")),
            )

        assertEquals(6, stream.readCalls)
        assertContentEquals(bytes, passthrough.response.body.readBytes())
    }

    @Test
    fun `encoded ambiguous MIME fails closed while encoded HTML remains untouched`() {
        val unknown = response(jpeg("encoded"), null, ChromeHttpHeader("Content-Encoding", "gzip"))
        val htmlBytes = "<html>compressed transport</html>".toByteArray()
        val htmlStream = ReadGuardInputStream(htmlBytes, maximumReads = 0)
        val html =
            response(
                htmlStream,
                htmlBytes.size.toLong(),
                "text/html",
                ChromeHttpHeader("Content-Encoding", "gzip"),
            )

        assertIs<ChromeImageContentInspection.Candidate>(authority.inspect(request(), unknown))
        assertIs<ChromeImageContentInspection.Passthrough>(authority.inspect(request(), html))
        assertEquals(0, htmlStream.readCalls)
    }

    @Test
    fun `ISO BMFF brand scan is bounded even when hostile box length covers the body`() {
        val bytes = ByteArray(16 * 1024)
        bytes[0] = 0x7f
        bytes[1] = 0xff.toByte()
        bytes[2] = 0xff.toByte()
        bytes[3] = 0xff.toByte()
        "ftyp".toByteArray().copyInto(bytes, 4)
        "avif".toByteArray().copyInto(bytes, 8)
        "avis".toByteArray().copyInto(bytes, 12)

        assertEquals(ChromeImageFormat.Avif, authority.sniffFormat(bytes))
        val candidate =
            assertIs<ChromeImageContentInspection.Candidate>(
                authority.inspect(request(ChromeHttpHeader("Sec-Fetch-Dest", "image")), response(bytes, null)),
            )
        assertIs<ChromeImageContentResolution.Reject>(authority.resolve(candidate, bytes))
    }

    @Test
    fun `mislabeled or absent MIME image enters authority by magic`() {
        listOf("application/octet-stream", "text/plain", null).forEach { contentType ->
            val inspection = authority.inspect(request(), response(jpeg("safe"), contentType))
            val candidate = assertIs<ChromeImageContentInspection.Candidate>(inspection)
            val resolution = assertIs<ChromeImageContentResolution.Inspect>(authority.resolve(candidate, jpeg("safe")))
            assertEquals("image/jpeg", resolution.format.canonicalMimeType)
        }
    }

    @Test
    fun `image intent and declared MIME route ambiguous bodies to fail closed authority`() {
        val html = "<html>not an image</html>".toByteArray()
        val byIntent =
            authority.inspect(
                request(ChromeHttpHeader("Sec-Fetch-Dest", "image")),
                response(html, "text/plain"),
            )
        val byMime = authority.inspect(request(), response(html, "image/png"))

        assertIs<ChromeImageContentResolution.Reject>(
            authority.resolve(assertIs<ChromeImageContentInspection.Candidate>(byIntent), html),
        )
        assertIs<ChromeImageContentResolution.Reject>(
            authority.resolve(assertIs<ChromeImageContentInspection.Candidate>(byMime), html),
        )
    }

    @Test
    fun `duplicate contradictory MIME cannot bypass and real format becomes canonical`() {
        val bytes = jpeg("safe")
        val candidate =
            assertIs<ChromeImageContentInspection.Candidate>(
                authority.inspect(
                    request(),
                    response(bytes, "image/png", ChromeHttpHeader("Content-Type", "text/html")),
                ),
            )

        assertEquals(listOf("image/png", "text/html"), candidate.declaredMimeTypes)
        val resolution = assertIs<ChromeImageContentResolution.Inspect>(authority.resolve(candidate, bytes))
        assertEquals(ChromeImageFormat.Jpeg, resolution.format)
    }

    @Test
    fun `SVG and animated containers always reject`() {
        val samples =
            listOf(
                "<svg><script/></svg>".toByteArray(),
                "GIF89a-animated".toByteArray(),
                png("acTL-animation"),
                webp(animated = true),
                avif("avis"),
            )
        samples.forEach { bytes ->
            val candidate =
                assertIs<ChromeImageContentInspection.Candidate>(
                    authority.inspect(
                        request(ChromeHttpHeader("Sec-Fetch-Dest", "image")),
                        response(bytes, "application/octet-stream"),
                    ),
                )
            assertIs<ChromeImageContentResolution.Reject>(authority.resolve(candidate, bytes))
        }
    }

    @Test
    fun `prefix peek replays non image bytes exactly without loss duplicate or reorder`() {
        val body = ByteArray(4097) { index -> (index % 251).toByte() }
        val inspected = authority.inspect(request(), response(body, "application/octet-stream"))
        val passthrough = assertIs<ChromeImageContentInspection.Passthrough>(inspected)

        assertContentEquals(body, passthrough.response.body.readBytes())
        assertEquals(body.size.toLong(), passthrough.response.bodyLength)
        assertEquals(1, authority.metrics().prefixPeeks)
    }

    @Test
    fun `body admission is bounded and saturation rejects without waiting`() {
        val bounded = ChromeImageContentAuthority(maximumConcurrentBodies = 1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        val first =
            worker.submit<String> {
                bounded.withBodyAdmission(onRejected = { "rejected" }) {
                    entered.countDown()
                    release.await(1, TimeUnit.SECONDS)
                    "accepted"
                }
            }
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        val second = bounded.withBodyAdmission(onRejected = { "rejected" }) { "accepted" }
        release.countDown()

        assertEquals("rejected", second)
        assertEquals("accepted", first.get(1, TimeUnit.SECONDS))
        assertEquals(1, bounded.metrics().bodyAdmissionPeak)
        assertEquals(1, bounded.metrics().bodyAdmissionRejects)
        worker.shutdownNow()
    }

    private fun request(vararg headers: ChromeHttpHeader) =
        ChromePhotosProxyRequest(
            method = "GET",
            target = "/image",
            headers = headers.toList(),
        )

    private fun response(
        bytes: ByteArray,
        contentType: String?,
        vararg extraHeaders: ChromeHttpHeader,
    ) = response(ByteArrayInputStream(bytes), bytes.size.toLong(), contentType, *extraHeaders)

    private fun response(
        body: InputStream,
        bodyLength: Long,
        contentType: String?,
        vararg extraHeaders: ChromeHttpHeader,
    ) = ChromePhotosUpstreamResponse(
        host = "example.com",
        statusCode = 200,
        statusText = "OK",
        headers = listOfNotNull(contentType?.let { ChromeHttpHeader("Content-Type", it) }) + extraHeaders,
        body = body,
        bodyLength = bodyLength,
        protocol = "h2",
    )

    private fun jpeg(body: String) = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + body.toByteArray()

    private fun png(body: String) =
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) + body.toByteArray()

    private fun webp(animated: Boolean = false): ByteArray {
        val bytes = ByteArray(24)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        "VP8X".toByteArray().copyInto(bytes, 12)
        if (animated) bytes[20] = 0x02
        return bytes
    }

    private fun avif(brand: String): ByteArray {
        val bytes = ByteArray(24)
        bytes[3] = bytes.size.toByte()
        "ftyp".toByteArray().copyInto(bytes, 4)
        brand.toByteArray().copyInto(bytes, 8)
        brand.toByteArray().copyInto(bytes, 16)
        return bytes
    }

    private class ReadGuardInputStream(
        private val bytes: ByteArray,
        private val maximumReads: Int,
    ) : InputStream() {
        var readCalls: Int = 0
            private set
        private var offset = 0

        override fun read(): Int {
            readCalls++
            if (readCalls > maximumReads) throw IOException("unexpected prefix read $readCalls")
            if (offset >= bytes.size) return -1
            return bytes[offset++].toInt() and 0xff
        }

        override fun read(
            target: ByteArray,
            targetOffset: Int,
            length: Int,
        ): Int {
            if (offset >= bytes.size) return -1
            val count = minOf(length, bytes.size - offset)
            bytes.copyInto(target, targetOffset, offset, offset + count)
            offset += count
            return count
        }
    }
}
