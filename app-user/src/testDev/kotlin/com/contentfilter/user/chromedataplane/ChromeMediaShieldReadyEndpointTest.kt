package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeMediaShieldReadyEndpointTest {
    @AfterTest
    fun tearDown() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun `exact issued top-level claim ACKs only after native presentation accepts`() {
        issue(Token, topLevel = true)
        val registration =
            ChromeMediaShieldReadyHandshakeBridge.register { claim, completion ->
                assertEquals(1L, claim.lifecycleSequence)
                assertEquals(
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(Token),
                    claim.identity.tokenDigest,
                )
                completion.acceptAfterOpaqueCommit()
            }
        try {
            val response = assertNotNull(ChromeMediaShieldReadyEndpoint().handle(request(Token, 1L)))

            assertEquals(204, response.statusCode)
            assertEquals(Origin, response.headers.firstValue("Access-Control-Allow-Origin"))
            assertEquals("no-store", response.headers.firstValue("Cache-Control"))
            assertContentEquals(ByteArray(0), response.bytes)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `unavailable native owner rejects and consumes no raw token outside request buffer`() {
        issue(Token, topLevel = true)
        val request = request(Token, 1L)

        val response = assertNotNull(ChromeMediaShieldReadyEndpoint().handle(request))

        assertEquals(503, response.statusCode)
        assertTrue(request.body.all { it == 0.toByte() })
        assertTrue(response.bytes.toString(Charsets.US_ASCII).contains("unavailable"))
    }

    @Test
    fun `duplicate lifecycle malformed metadata and subdocument stay fail closed`() {
        issue(Token, topLevel = true)
        issue(FrameToken, topLevel = false)
        val registration =
            ChromeMediaShieldReadyHandshakeBridge.register { _, completion -> completion.acceptAfterOpaqueCommit() }
        try {
            val endpoint = ChromeMediaShieldReadyEndpoint()
            assertEquals(204, endpoint.handle(request(Token, 1L))?.statusCode)
            assertEquals(503, endpoint.handle(request(Token, 1L))?.statusCode)
            assertEquals(503, endpoint.handle(request(FrameToken, 1L))?.statusCode)
            assertEquals(204, endpoint.handle(request(Token, 3L))?.statusCode)
            assertEquals(503, endpoint.handle(request(Token, 2L))?.statusCode)

            val malformed = request(Token, 4L).copy(headers = listOf(ChromeHttpHeader("Origin", Origin)))
            assertEquals(503, endpoint.handle(malformed)?.statusCode)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `only fixed path is intercepted and wrong method is rejected`() {
        val endpoint = ChromeMediaShieldReadyEndpoint()
        assertNull(endpoint.handle(request(Token, 1L).copy(target = "/other")))
        assertEquals(405, endpoint.handle(request(Token, 1L).copy(method = "GET"))?.statusCode)
    }

    @Test
    fun `null origin and non-canonical content type fail before consuming authority`() {
        issue(Token, topLevel = true)
        val endpoint = ChromeMediaShieldReadyEndpoint()
        val registration =
            ChromeMediaShieldReadyHandshakeBridge.register { _, completion -> completion.acceptAfterOpaqueCommit() }
        try {
            val nullOrigin =
                request(Token, 1L).copy(
                    headers =
                        request(Token, 1L).headers.map { header ->
                            if (header.name.equals("Origin", ignoreCase = true)) {
                                header.copy(value = "null")
                            } else {
                                header
                            }
                        },
                )
            assertEquals(503, endpoint.handle(nullOrigin)?.statusCode)

            val broadContentType =
                request(Token, 1L).copy(
                    headers =
                        request(Token, 1L).headers.map { header ->
                            if (header.name.equals("Content-Type", ignoreCase = true)) {
                                header.copy(value = "text/plain; charset=us-ascii")
                            } else {
                                header
                            }
                        },
                )
            assertEquals(503, endpoint.handle(broadContentType)?.statusCode)

            assertEquals(204, endpoint.handle(request(Token, 1L))?.statusCode)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `exact CORS private-network preflight is local and carries no authority`() {
        val endpoint = ChromeMediaShieldReadyEndpoint()
        val request =
            request(Token, 1L).copy(
                method = "OPTIONS",
                headers =
                    listOf(
                        ChromeHttpHeader("Origin", Origin),
                        ChromeHttpHeader("Sec-Fetch-Mode", "cors"),
                        ChromeHttpHeader("Sec-Fetch-Dest", "empty"),
                        ChromeHttpHeader("Access-Control-Request-Method", "POST"),
                        ChromeHttpHeader("Access-Control-Request-Headers", "content-type"),
                        ChromeHttpHeader("Access-Control-Request-Private-Network", "true"),
                    ),
                body = ByteArray(0),
            )

        val response = assertNotNull(endpoint.handle(request))

        assertEquals(204, response.statusCode)
        assertEquals("true", response.headers.firstValue("Access-Control-Allow-Private-Network"))
        assertEquals(1L, endpoint.metrics().preflights)
        assertEquals(0L, endpoint.metrics().accepted)
    }

    private fun issue(
        token: String,
        topLevel: Boolean,
    ) {
        if (ChromeMediaShieldDocumentAuthorityRegistry.snapshot().protectionSessionId.isBlank()) {
            ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        }
        requireNotNull(
            ChromeMediaShieldDocumentAuthorityRegistry.issue(
                Session,
                PolicyEpoch,
                token,
                topLevel,
            ),
        )
    }

    private fun request(
        token: String,
        lifecycle: Long,
    ) = ChromePhotosProxyRequest(
        method = "POST",
        target = ChromePhotosDataPlaneLabContract.MediaShieldReadyPath,
        headers =
            listOf(
                ChromeHttpHeader("Origin", Origin),
                ChromeHttpHeader("Content-Type", "text/plain;charset=UTF-8"),
                ChromeHttpHeader("Sec-Fetch-Mode", "cors"),
                ChromeHttpHeader("Sec-Fetch-Dest", "empty"),
            ),
        body = "v1|$token|$lifecycle".toByteArray(Charsets.US_ASCII),
        bodyFraming = ChromeHttpBodyFraming.ContentLength,
    )

    private companion object {
        const val Session = "h19-ready-session"
        const val PolicyEpoch = 19L
        const val Origin = "https://shop.example"
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val FrameToken = "BBBBBBBBBBBBBBBBBBBBBB"
    }
}
