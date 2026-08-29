package com.contentfilter.user.chromedataplane

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeMediaShieldDocumentAdmissionTest {
    private val admission = ChromeMediaShieldDocumentAdmission()

    @Test
    fun `document intent requests identity encoding and removes partial or cache validators`() {
        val request =
            request(
                "GET",
                ChromeHttpHeader("Sec-Fetch-Dest", "document"),
                ChromeHttpHeader("Accept-Encoding", "gzip, br"),
                ChromeHttpHeader("Range", "bytes=0-99"),
                ChromeHttpHeader("If-None-Match", "old"),
                ChromeHttpHeader("Cookie", "session=value"),
            )

        val normalized = admission.normalizeUpstreamRequest(request)

        assertEquals(listOf("identity"), normalized.headerValues("Accept-Encoding"))
        assertEquals(emptyList(), normalized.headerValues("Range"))
        assertEquals(emptyList(), normalized.headerValues("If-None-Match"))
        assertEquals("session=value", normalized.firstHeader("Cookie"))
    }

    @Test
    fun `XHR HTML is not transformed while navigable unsupported content fails closed`() {
        val xhr = request("GET", ChromeHttpHeader("Sec-Fetch-Dest", "empty"))
        val document = request("GET", ChromeHttpHeader("Sec-Fetch-Dest", "document"))

        assertIs<ChromeMediaShieldDocumentDisposition.NotDocument>(
            admission.disposition(xhr, response("text/html; charset=utf-8")),
        )
        assertIs<ChromeMediaShieldDocumentDisposition.FailClosed>(
            admission.disposition(document, response("application/pdf")),
        )
        assertIs<ChromeMediaShieldDocumentDisposition.FailClosed>(
            admission.disposition(document, response("text/html; charset=utf-16")),
        )
    }

    @Test
    fun `top-level and iframe are distinct document kinds`() {
        val top =
            assertIs<ChromeMediaShieldDocumentDisposition.Transform>(
                admission.disposition(
                    request("GET", ChromeHttpHeader("Sec-Fetch-Dest", "document")),
                    response("text/html; charset=utf-8"),
                ),
            )
        val frame =
            assertIs<ChromeMediaShieldDocumentDisposition.Transform>(
                admission.disposition(
                    request("GET", ChromeHttpHeader("Sec-Fetch-Dest", "iframe")),
                    response("text/html; charset=windows-1252"),
                ),
            )

        assertEquals(ChromeMediaShieldDocumentKind.TopLevel, top.kind)
        assertEquals(ChromeMediaShieldDocumentKind.Subdocument, frame.kind)
        listOf("frame", "fencedframe").forEach { destination ->
            assertEquals(
                ChromeMediaShieldDocumentKind.Subdocument,
                assertIs<ChromeMediaShieldDocumentDisposition.Transform>(
                    admission.disposition(
                        request("GET", ChromeHttpHeader("Sec-Fetch-Dest", destination)),
                        response("text/html; charset=utf-8"),
                    ),
                ).kind,
            )
        }
    }

    @Test
    fun `top-level POST navigation is governed while XHR POST remains outside document transformer`() {
        val navigation =
            request(
                "POST",
                ChromeHttpHeader("Sec-Fetch-Dest", "document"),
                ChromeHttpHeader("Sec-Fetch-Mode", "navigate"),
            )
        val xhr = request("POST", ChromeHttpHeader("Sec-Fetch-Dest", "empty"))

        assertTrue(admission.governs(navigation))
        assertIs<ChromeMediaShieldDocumentDisposition.Transform>(
            admission.disposition(navigation, response("text/html; charset=utf-8")),
        )
        assertFalse(admission.governs(xhr))
        assertIs<ChromeMediaShieldDocumentDisposition.NotDocument>(
            admission.disposition(xhr, response("text/html; charset=utf-8")),
        )
    }

    @Test
    fun `missing fetch metadata uses strict navigation hint or fails closed for ambiguous HTML`() {
        val fallbackNavigation =
            request(
                "GET",
                ChromeHttpHeader("Upgrade-Insecure-Requests", "1"),
                ChromeHttpHeader("Accept", "text/html,application/xhtml+xml"),
            )
        val ambiguous = request("GET", ChromeHttpHeader("Accept", "*/*"))
        val json = response("application/json")

        assertTrue(admission.governs(fallbackNavigation))
        assertEquals(
            ChromeMediaShieldDocumentKind.TopLevel,
            assertIs<ChromeMediaShieldDocumentDisposition.Transform>(
                admission.disposition(fallbackNavigation, response("text/html; charset=utf-8")),
            ).kind,
        )
        val normalizedFallback = admission.normalizeUpstreamRequest(fallbackNavigation)
        assertEquals(listOf("identity"), normalizedFallback.headerValues("Accept-Encoding"))
        assertFalse(admission.governs(ambiguous))
        assertTrue(admission.requiresBufferedDecision(ambiguous, response("text/html; charset=utf-8")))
        assertIs<ChromeMediaShieldDocumentDisposition.FailClosed>(
            admission.disposition(ambiguous, response("text/html; charset=utf-8")),
        )
        assertFalse(admission.requiresBufferedDecision(ambiguous, json))
        assertIs<ChromeMediaShieldDocumentDisposition.NotDocument>(admission.disposition(ambiguous, json))
        val missingContentType = responseWithoutContentType()
        assertTrue(admission.requiresBufferedDecision(ambiguous, missingContentType))
        assertIs<ChromeMediaShieldDocumentDisposition.FailClosed>(
            admission.disposition(ambiguous, missingContentType),
        )
    }

    private fun request(
        method: String,
        vararg headers: ChromeHttpHeader,
    ) = ChromePhotosProxyRequest(method, "/", headers = headers.toList())

    private fun response(contentType: String) =
        ChromePhotosUpstreamResponse(
            host = "example.test",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(ChromeHttpHeader("Content-Type", contentType)),
            body = ByteArrayInputStream(ByteArray(0)),
            bodyLength = 0L,
            protocol = "http/1.1",
        )

    private fun responseWithoutContentType() =
        ChromePhotosUpstreamResponse(
            host = "example.test",
            statusCode = 200,
            statusText = "OK",
            headers = emptyList(),
            body = ByteArrayInputStream(ByteArray(0)),
            bodyLength = 0L,
            protocol = "http/1.1",
        )
}
