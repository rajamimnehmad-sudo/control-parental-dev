package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeMediaContentAuthorityTest {
    private val placeholder = "placeholder".toByteArray()

    @Test
    fun `media request removes range and validators before authority`() {
        val authority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe })
        val request =
            request(
                ChromeHttpHeader("Sec-Fetch-Dest", "video"),
                ChromeHttpHeader("Range", "bytes=0-99"),
                ChromeHttpHeader("If-Range", "\"old\""),
                ChromeHttpHeader("Accept-Encoding", "gzip"),
            )

        val normalized = authority.normalizeUpstreamRequest(request)

        assertTrue(normalized.headerValues("Range").isEmpty())
        assertTrue(normalized.headerValues("If-Range").isEmpty())
        assertEquals(listOf("identity"), normalized.headerValues("Accept-Encoding"))
    }

    @Test
    fun `declared MP4 and HLS are candidates while unrelated traffic passes through`() {
        val authority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe })

        assertIs<ChromeMediaContentInspection.Candidate>(authority.inspect(requestFor("/asset"), response("video/mp4")))
        assertIs<ChromeMediaContentInspection.Candidate>(authority.inspect(requestFor("/segment.ts"), response("video/mp2t")))
        val inferredSegment =
            assertIs<ChromeMediaContentInspection.Candidate>(
                authority.inspect(requestFor("/segment.ts"), response("application/octet-stream")),
            )
        assertEquals(ChromeMediaKind.HlsSegment, inferredSegment.kind)
        assertEquals("video/mp2t", inferredSegment.declaredMimeType)
        val hls = assertIs<ChromeMediaContentInspection.Candidate>(authority.inspect(requestFor("/asset"), response("application/vnd.apple.mpegurl")))
        assertEquals(ChromeMediaKind.HlsManifest, hls.kind)
        assertIs<ChromeMediaContentInspection.Passthrough>(authority.inspect(requestFor("/asset"), response("application/json")))
    }

    @Test
    fun `clear HLS manifest is safe and encrypted or unsafe references fail closed`() {
        val safe =
            "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4.0,\nsegment-1.ts\n#EXTINF:4.0,\nhttps://cdn.example/segment-2.ts\n#EXTINF:4.0,\n//media.example/segment-3.ts\n"
        val encrypted = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\nsegment.ts\n"
        val unsafe = "#EXTM3U\n#EXTINF:4,\ndata:text/plain,raw\n"

        assertEquals(ChromeMediaPayloadDecision.Safe, ChromeHlsManifestPolicy.inspect(safe.toByteArray()))
        assertEquals(
            ChromeMediaPayloadDecision.Unknown("hls_encryption_not_inspectable"),
            ChromeHlsManifestPolicy.inspect(encrypted.toByteArray()),
        )
        assertEquals(
            ChromeMediaPayloadDecision.Unknown("hls_segment_uri_unsafe"),
            ChromeHlsManifestPolicy.inspect(unsafe.toByteArray()),
        )
    }

    @Test
    fun `media body admission surrounds bounded reads and records peak`() {
        val authority = ChromeMediaContentAuthority(
            ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe },
            maximumConcurrentBodies = 1,
        )

        val result = authority.withBodyAdmission(
            onRejected = { "rejected" },
            block = { "admitted" },
        )

        assertEquals("admitted", result)
        assertEquals(1, authority.metrics().bodyAdmissionPeak)
        assertEquals(0, authority.metrics().bodyAdmissionRejects)
    }

    @Test
    fun `identical complete media reuses the bounded payload decision`() {
        var inspections = 0
        val authority =
            ChromeMediaContentAuthority(
                ChromeMediaPayloadInspector { _, _ ->
                    inspections++
                    ChromeMediaPayloadDecision.Safe
                },
            )
        val bytes = "complete-media".toByteArray()
        val candidate = candidate(bytes)

        assertEquals(ChromeMediaPayloadDecision.Safe, authority.inspectPayload(candidate, bytes))
        assertEquals(ChromeMediaPayloadDecision.Safe, authority.inspectPayload(candidate, bytes.copyOf()))
        assertEquals(1, inspections)
    }

    @Test
    fun `authorized range produces RFC-compatible 206 without leaking source bytes`() {
        val source = "0123456789".toByteArray()
        val authority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe })
        val sanitizer = ChromeMediaResponseSanitizer(authority, placeholderBytes = placeholder, maximumMediaBytes = 64)
        val request = request(ChromeHttpHeader("Range", "bytes=2-5"))
        val candidate =
            ChromeMediaContentInspection.Candidate(
                response = response("video/mp4", source),
                kind = ChromeMediaKind.ProgressiveVideo,
                declaredMimeType = "video/mp4",
                requestIntent = true,
            )

        val result = sanitizer.sanitize("GET", request, candidate)

        assertEquals(206, result.statusCode)
        assertContentEquals("2345".toByteArray(), result.bytes)
        assertEquals("bytes 2-5/10", result.headers.firstValue("Content-Range"))
        assertEquals("bytes", result.headers.firstValue("Accept-Ranges"))
        assertEquals(1, result.headers.count { it.name.equals("Content-Type", true) })
        assertFalse(result.bytes.contentEquals(source))
        assertEquals(ChromePhotosResourceDecision.Safe, result.decision)
    }

    @Test
    fun `blocked and unknown media never deliver source bytes`() {
        val source = "sensitive-media".toByteArray()
        val authority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Block })
        val sanitizer = ChromeMediaResponseSanitizer(authority, placeholderBytes = placeholder, maximumMediaBytes = 64)
        val candidate = candidate(source)

        val blocked = sanitizer.sanitize("GET", request(), candidate)
        assertEquals(ChromePhotosResourceDecision.Block, blocked.decision)
        assertContentEquals(placeholder, blocked.bytes)
        assertFalse(blocked.bytes.contentEquals(source))

        val unknownAuthority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Unknown("decode") })
        val unknown = ChromeMediaResponseSanitizer(unknownAuthority, placeholderBytes = placeholder, maximumMediaBytes = 64)
            .sanitize("GET", request(), candidate(source))
        assertEquals(ChromePhotosResourceDecision.Unknown, unknown.decision)
        assertContentEquals(placeholder, unknown.bytes)
        assertFalse(unknown.bytes.contentEquals(source))
    }

    @Test
    fun `multiple or reversed ranges fail closed`() {
        val authority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe })
        val sanitizer = ChromeMediaResponseSanitizer(authority, placeholderBytes = placeholder, maximumMediaBytes = 64)
        val candidate = candidate("0123456789".toByteArray())

        val multiple = sanitizer.sanitize("GET", request(ChromeHttpHeader("Range", "bytes=0-1,4-5")), candidate)
        val reversed = sanitizer.sanitize("GET", request(ChromeHttpHeader("Range", "bytes=8-2")), candidate)

        assertEquals(ChromePhotosResourceDecision.Unknown, multiple.decision)
        assertEquals(ChromePhotosResourceDecision.Unknown, reversed.decision)
        assertContentEquals(placeholder, multiple.bytes)
        assertContentEquals(placeholder, reversed.bytes)
    }

    private fun candidate(bytes: ByteArray) =
        ChromeMediaContentInspection.Candidate(
            response = response("video/mp4", bytes),
            kind = ChromeMediaKind.ProgressiveVideo,
            declaredMimeType = "video/mp4",
            requestIntent = true,
        )

    private fun request(vararg headers: ChromeHttpHeader) = requestFor("/asset.mp4", *headers)

    private fun requestFor(
        target: String,
        vararg headers: ChromeHttpHeader,
    ) = ChromePhotosProxyRequest("GET", target, headers = headers.toList())

    private fun response(contentType: String, bytes: ByteArray = ByteArray(0)) =
        ChromePhotosUpstreamResponse(
            host = "example.com",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(ChromeHttpHeader("Content-Type", contentType)),
            body = bytes.inputStream(),
            bodyLength = bytes.size.toLong(),
            protocol = "h2",
        )
}
