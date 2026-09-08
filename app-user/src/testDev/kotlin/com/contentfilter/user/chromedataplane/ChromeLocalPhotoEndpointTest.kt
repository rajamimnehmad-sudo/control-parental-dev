package com.contentfilter.user.chromedataplane

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChromeLocalPhotoEndpointTest {
    private val safe = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + "safe".toByteArray()
    private val blocked = safe + "blocked".toByteArray()
    private val placeholder = "replacement-only".toByteArray()
    private val gate = ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = placeholder)
    private var valid = true
    private val sanitizer =
        ChromePhotosRealResponseSanitizer(
            ChromePhotosResourceTransformer(listOf(safe), listOf(blocked), placeholder),
            ChromePublicDestinationAuthority(),
            placeholder,
        )

    private fun endpoint(maximum: Int = 1024) =
        ChromeLocalPhotoEndpoint(
            sanitize = { sanitizer.sanitize("GET", it) },
            authorize = gate::isCandidateDeliveryAuthorized,
            validates = { token, identity -> valid && token == "owner" && identity.protectionSessionId == "session" },
            maximumStoredBytes = maximum,
        )

    private fun submit(
        bytes: ByteArray = safe,
        mime: String = "image/png",
        token: String = "owner",
    ) = ChromePhotosProxyRequest(
        "POST",
        ChromeLocalPhotoEndpoint.SubmitPath,
        headers =
            listOf(
                ChromeHttpHeader("Origin", "https://example.com"),
                ChromeHttpHeader("Content-Type", "text/plain;charset=UTF-8"),
            ),
        body = ("v1|LOCAL_PHOTO|$token|session|1|1|1|T\ndata:$mime;base64," + Base64.getEncoder().encodeToString(bytes)).toByteArray(),
    )

    private fun url(
        endpoint: ChromeLocalPhotoEndpoint,
        request: ChromePhotosProxyRequest = submit(),
    ): String {
        val response = assertNotNull(endpoint.handle("https://example.com", request))
        assertEquals(200, response.statusCode)
        return response.bytes.toString(Charsets.UTF_8).removePrefix(ChromeLocalPhotoEndpoint.AssetOrigin)
    }

    private fun get(
        endpoint: ChromeLocalPhotoEndpoint,
        path: String,
        origin: String = ChromeLocalPhotoEndpoint.AssetOrigin,
    ) = assertNotNull(endpoint.handle(origin, ChromePhotosProxyRequest("GET", path)))

    @Test fun `SAFE is byte identical and BLOCK UNKNOWN never retain raw candidates`() {
        endpoint().use { e ->
            assertContentEquals(safe, get(e, url(e)).bytes)
            assertContentEquals(placeholder, get(e, url(e, submit(blocked))).bytes)
            assertContentEquals(placeholder, get(e, url(e, submit(safe + "unknown".toByteArray()))).bytes)
        }
    }

    @Test fun `origin and document capability are required before inference`() {
        endpoint().use { e ->
            assertEquals(403, e.handle("https://other.example", submit())!!.statusCode)
            assertEquals(403, e.handle("https://example.com", submit(token = "other"))!!.statusCode)
            val path = url(e)
            assertEquals(403, get(e, path, "https://other.example").statusCode)
            valid = false
            assertEquals(410, get(e, path).statusCode)
        }
    }

    @Test fun `session invalidated during decision cannot publish a result`() {
        val e =
            ChromeLocalPhotoEndpoint(sanitize = {
                sanitizer.sanitize("GET", it).also { valid = false }
            }, authorize = gate::isCandidateDeliveryAuthorized, validates = {
                    _,
                    _,
                ->
                valid
            })
        assertEquals(403, e.handle("https://example.com", submit())!!.statusCode)
        assertFalse(e.metrics().contains("entries=1"))
        e.close()
    }

    @Test fun `Byte Gate rejects fabricated SAFE and raw UNKNOWN responses`() {
        for (decision in listOf(
            ChromePhotosResourceDecision.Safe,
            ChromePhotosResourceDecision.Unknown,
            ChromePhotosResourceDecision.Passthrough,
        )) {
            ChromeLocalPhotoEndpoint(sanitize = {
                ChromePhotosSanitizedResponse(
                    200,
                    "OK",
                    listOf(ChromeHttpHeader("Content-Type", "image/png")),
                    safe,
                    decision,
                    false,
                    sha256(safe),
                    safe.size,
                )
            }, authorize = gate::isCandidateDeliveryAuthorized, validates = {
                    _,
                    _,
                ->
                true
            }).use { e ->
                assertEquals(415, e.handle("https://example.com", submit())!!.statusCode)
            }
        }
    }

    @Test fun `bounded assets evict and close invalidates capabilities without changing delivered bytes`() {
        val e = endpoint(safe.size * 2)
        val first = url(e)
        url(e)
        val last = url(e)
        assertEquals(410, get(e, first).statusCode)
        val delivered = get(e, last).bytes
        e.close()
        assertEquals(410, get(e, last).statusCode)
        assertContentEquals(safe, delivered)
    }

    @Test fun `unsupported and spoofed SVG never return original bytes`() {
        endpoint().use { e ->
            assertEquals(415, e.handle("https://example.com", submit("GIF89a".toByteArray(), "image/gif"))!!.statusCode)
            val r = get(e, url(e, submit("<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray())))
            assertContentEquals(placeholder, r.bytes)
        }
    }

    @Test fun `data decoding preserves plus and rejects invalid alphabets and overflow`() {
        val value = "data:image/webp;base64," + Base64.getEncoder().encodeToString(byteArrayOf(251.toByte(), 239.toByte()))
        assertContentEquals(
            byteArrayOf(251.toByte(), 239.toByte()),
            assertNotNull(ChromeLocalPhotoDataUrl.decode(value)).bytes,
        )
        assertNull(ChromeLocalPhotoDataUrl.decode("data:image/png;base64,AA!="))
        assertNull(ChromeLocalPhotoDataUrl.decode("data:text/html;base64,AA=="))
        assertNull(
            ChromeLocalPhotoDataUrl.decode(
                "data:image/png;base64," + "A".repeat(ChromeLocalPhotoEndpoint.MaximumRequestBytes),
            ),
        )
    }

    @Test fun `local upload body limit is enforced before body read`() {
        val wire = "POST ${ChromeLocalPhotoEndpoint.SubmitPath} HTTP/1.1\r\nHost: example.com\r\nContent-Length: ${ChromeLocalPhotoEndpoint.MaximumRequestBytes + 1}\r\n\r\n"
        for (request in listOf(wire, wire.replace("POST /", "POST http://example.com/"))) {
            val e =
                kotlin.test.assertFailsWith<ChromeHttpProtocolException> {
                    ChromeHttp1RequestReader().read(request.byteInputStream())
                }
            assertEquals(413, e.statusCode)
        }
    }
}
