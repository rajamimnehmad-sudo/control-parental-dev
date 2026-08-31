package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierBridge
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeMediaShieldReadyEndpointTest {
    @AfterTest
    fun tearDown() {
        ChromeMediaShieldDocumentAuthorityRegistry.clear()
        ChromePhotosDataPlaneRuntimeAttestation.clear()
    }

    @Test
    fun `H20 self ready accepts exact document once without active document owner`() {
        val identity = issueH20(Token, topLevel = true)
        attestH20()
        val endpoint = ChromeMediaShieldReadyEndpoint(documentSelfShieldEnabled = true, elapsedRealtime = { Now })

        assertEquals(204, endpoint.handle(selfReadyRequest(Token, identity))?.statusCode)
        assertEquals(503, endpoint.handle(selfReadyRequest(Token, identity))?.statusCode)
        assertEquals(1L, endpoint.metrics().selfReadyAccepted)
        assertEquals(1L, endpoint.metrics().selfReadyRejected)
        assertEquals(0L, endpoint.metrics().activeHello)
    }

    @Test
    fun `H20 identity mismatch and health loss retain document curtain authority`() {
        val identity = issueH20(Token, topLevel = true)
        attestH20()
        val endpoint = ChromeMediaShieldReadyEndpoint(documentSelfShieldEnabled = true, elapsedRealtime = { Now })

        assertEquals(
            503,
            endpoint.handle(selfReadyRequest(Token, identity.copy(documentSequence = identity.documentSequence + 1L)))
                ?.statusCode,
        )
        ChromePhotosDataPlaneRuntimeAttestation.failClosed(Session)
        assertEquals(503, endpoint.handle(selfReadyRequest(Token, identity))?.statusCode)
        assertEquals(0L, endpoint.metrics().selfReadyAccepted)
    }

    @Test
    fun `active document phases are exact one shot and PRESENT ACK follows native acceptance`() {
        issue(Token, topLevel = true)
        val registration =
            ChromeMediaShieldActiveDocumentHandshakeBridge.register { request, completion ->
                assertEquals(1L, request.claim.lifecycleSequence)
                assertEquals(
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(Token),
                    request.claim.identity.tokenDigest,
                )
                when (request) {
                    is ChromeMediaShieldActiveDocumentRequest.Hello ->
                        completion.issueChallenge(challenge())
                    is ChromeMediaShieldActiveDocumentRequest.Prove -> {
                        assertEquals(challenge(), request.challenge)
                        completion.acceptProof()
                    }
                    is ChromeMediaShieldActiveDocumentRequest.Present -> {
                        assertEquals(challenge(), request.challenge)
                        completion.acceptPresentation()
                    }
                    is ChromeMediaShieldActiveDocumentRequest.Revoke -> {
                        assertEquals(challenge(), request.challenge)
                        completion.acceptRevocation()
                    }
                }
            }
        try {
            val endpoint = ChromeMediaShieldReadyEndpoint()
            val hello = assertNotNull(endpoint.handle(request("HELLO", Token, 1L)))
            assertEquals(200, hello.statusCode)
            assertEquals("v2|CHALLENGE|$Challenge", hello.bytes.toString(Charsets.US_ASCII))
            assertEquals(204, endpoint.handle(request("PROVE", Token, 1L, Challenge))?.statusCode)
            assertEquals(204, endpoint.handle(request("PRESENT", Token, 1L, Challenge))?.statusCode)
            assertEquals(204, endpoint.handle(request("REVOKE", Token, 1L, Challenge))?.statusCode)
            assertEquals(Origin, hello.headers.firstValue("Access-Control-Allow-Origin"))
            assertEquals("no-store", hello.headers.firstValue("Cache-Control"))
            val metrics = endpoint.metrics()
            assertEquals(1L, metrics.activeHello)
            assertEquals(1L, metrics.challengeIssued)
            assertEquals(1L, metrics.proofAccepted)
            assertEquals(1L, metrics.presentAccepted)
            assertEquals(1L, metrics.revokeAccepted)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `parser barrier is fixed tokenless no-store JS and grants no document claim`() {
        val registration = ChromeMediaShieldParserBarrierBridge.register { completion -> completion.ready() }
        try {
            val endpoint = ChromeMediaShieldReadyEndpoint()
            val response = assertNotNull(endpoint.handle(parserBarrierRequest()))

            assertEquals(200, response.statusCode)
            assertEquals(
                "self.__gloshH19ParserBarrierCommit__&&self.__gloshH19ParserBarrierCommit__(true);",
                response.bytes.toString(Charsets.US_ASCII),
            )
            assertTrue(response.bytes.toString(Charsets.US_ASCII).contains(Token).not())
            assertEquals("no-store", response.headers.firstValue("Cache-Control"))
            assertEquals("nosniff", response.headers.firstValue("X-Content-Type-Options"))
            assertEquals("cross-origin", response.headers.firstValue("Cross-Origin-Resource-Policy"))
            assertEquals("application/javascript; charset=us-ascii", response.headers.firstValue("Content-Type"))
            assertEquals(null, response.headers.firstValue("Access-Control-Allow-Origin"))
            assertEquals(0, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().readyClaims)
            assertEquals(1L, endpoint.metrics().parserBarrierRequests)
            assertEquals(1L, endpoint.metrics().parserBarrierReady)
            assertEquals(0L, endpoint.metrics().parserBarrierFailClosed)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `parser barrier unavailable or malformed returns executable fail-closed guard signal`() {
        val endpoint = ChromeMediaShieldReadyEndpoint()
        val unavailable = assertNotNull(endpoint.handle(parserBarrierRequest()))
        val wrongDestination =
            assertNotNull(
                endpoint.handle(
                    parserBarrierRequest().copy(
                        headers =
                            listOf(
                                ChromeHttpHeader("Sec-Fetch-Mode", "no-cors"),
                                ChromeHttpHeader("Sec-Fetch-Dest", "document"),
                            ),
                    ),
                ),
            )

        assertEquals(200, unavailable.statusCode)
        assertTrue(unavailable.bytes.toString(Charsets.US_ASCII).contains("ParserBarrierCommit__(false)"))
        assertTrue(wrongDestination.bytes.toString(Charsets.US_ASCII).contains("ParserBarrierCommit__(false)"))
        assertEquals(2L, endpoint.metrics().parserBarrierFailClosed)
    }

    @Test
    fun `unavailable native owner rejects and zeroes the raw request token`() {
        issue(Token, topLevel = true)
        val request = request("HELLO", Token, 1L)

        val response = assertNotNull(ChromeMediaShieldReadyEndpoint().handle(request))

        assertEquals(503, response.statusCode)
        assertTrue(request.body.all { it == 0.toByte() })
        assertTrue(response.bytes.toString(Charsets.US_ASCII).contains("unavailable"))
    }

    @Test
    fun `duplicate lifecycle malformed challenge and subdocument stay fail closed`() {
        issue(Token, topLevel = true)
        issue(FrameToken, topLevel = false)
        val registration =
            ChromeMediaShieldActiveDocumentHandshakeBridge.register { request, completion ->
                when (request) {
                    is ChromeMediaShieldActiveDocumentRequest.Hello -> completion.issueChallenge(challenge())
                    else -> completion.reject()
                }
            }
        try {
            val endpoint = ChromeMediaShieldReadyEndpoint()
            assertEquals(200, endpoint.handle(request("HELLO", Token, 1L))?.statusCode)
            assertEquals(503, endpoint.handle(request("HELLO", Token, 1L))?.statusCode)
            assertEquals(503, endpoint.handle(request("HELLO", FrameToken, 1L))?.statusCode)
            assertEquals(200, endpoint.handle(request("HELLO", Token, 3L))?.statusCode)
            assertEquals(503, endpoint.handle(request("HELLO", Token, 2L))?.statusCode)
            assertEquals(503, endpoint.handle(request("PROVE", Token, 3L, "bad challenge"))?.statusCode)

            val malformed =
                request("HELLO", Token, 4L).copy(headers = listOf(ChromeHttpHeader("Origin", Origin)))
            assertEquals(503, endpoint.handle(malformed)?.statusCode)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `only fixed path and strict v2 grammar are accepted`() {
        val endpoint = ChromeMediaShieldReadyEndpoint()
        assertNull(endpoint.handle(request("HELLO", Token, 1L).copy(target = "/other")))
        assertEquals(405, endpoint.handle(request("HELLO", Token, 1L).copy(method = "GET"))?.statusCode)
        assertEquals(
            503,
            endpoint.handle(request("HELLO", Token, 1L).copy(body = "v1|$Token|1".toByteArray()))?.statusCode,
        )
        assertEquals(503, endpoint.handle(request("UNKNOWN", Token, 1L))?.statusCode)
    }

    @Test
    fun `null origin and non-canonical content type fail before consuming authority`() {
        issue(Token, topLevel = true)
        val endpoint = ChromeMediaShieldReadyEndpoint()
        val registration =
            ChromeMediaShieldActiveDocumentHandshakeBridge.register { _, completion ->
                completion.issueChallenge(challenge())
            }
        try {
            val nullOrigin =
                request("HELLO", Token, 1L).copy(
                    headers =
                        request("HELLO", Token, 1L).headers.map { header ->
                            if (header.name.equals("Origin", ignoreCase = true)) {
                                header.copy(value = "null")
                            } else {
                                header
                            }
                        },
                )
            assertEquals(503, endpoint.handle(nullOrigin)?.statusCode)

            val broadContentType =
                request("HELLO", Token, 1L).copy(
                    headers =
                        request("HELLO", Token, 1L).headers.map { header ->
                            if (header.name.equals("Content-Type", ignoreCase = true)) {
                                header.copy(value = "text/plain; charset=us-ascii")
                            } else {
                                header
                            }
                        },
                )
            assertEquals(503, endpoint.handle(broadContentType)?.statusCode)
            assertEquals(200, endpoint.handle(request("HELLO", Token, 1L))?.statusCode)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `exact CORS private-network preflight is local and carries no authority`() {
        val endpoint = ChromeMediaShieldReadyEndpoint()
        val request =
            request("HELLO", Token, 1L).copy(
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

    private fun issueH20(
        token: String,
        topLevel: Boolean,
    ) = run {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, H20PolicyEpoch)
        requireNotNull(
            ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, H20PolicyEpoch, token, topLevel),
        )
    }

    private fun attestH20() {
        ChromePhotosDataPlaneRuntimeAttestation.beginSession(
            sessionId = Session,
            mediaAuthorityEnabled = true,
            mediaPolicyEpoch = H20PolicyEpoch,
            documentSelfShieldEnabled = true,
        )
        ChromePhotosDataPlaneRuntimeAttestation.markProxyHealthy(Session, true)
        ChromePhotosDataPlaneRuntimeAttestation.markPolicyConfirmed(Session, true)
        ChromePhotosDataPlaneRuntimeAttestation.markVpnConfirmed(Session, true)
        ChromePhotosDataPlaneRuntimeAttestation.markFixtureConfirmed(Session, true, Now - 10L)
        ChromePhotosDataPlaneRuntimeAttestation.publishHeartbeat(Session, Now - 10L, Now + 500L)
    }

    private fun selfReadyRequest(
        token: String,
        identity: com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity,
    ) = request("HELLO", token, 1L).copy(
        target = ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath,
        body =
            (
                "v3|SELF_READY|$token|${identity.protectionSessionId}|${identity.policyEpoch}|" +
                    "${identity.navigationSequence}|${identity.documentSequence}|1|" +
                    if (identity.topLevel) "T" else "S"
            ).toByteArray(Charsets.US_ASCII),
    )

    private fun request(
        phase: String,
        token: String,
        lifecycle: Long,
        challenge: String? = null,
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
        body =
            buildString {
                append("v2|")
                append(phase)
                append('|')
                append(token)
                append('|')
                append(lifecycle)
                if (challenge != null) {
                    append('|')
                    append(challenge)
                }
            }.toByteArray(Charsets.US_ASCII),
        bodyFraming = ChromeHttpBodyFraming.ContentLength,
    )

    private fun challenge() = ChromeMediaShieldActiveDocumentChallenge.fromEncoded(Challenge)

    private fun parserBarrierRequest() =
        ChromePhotosProxyRequest(
            method = "GET",
            target = ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierPath,
            headers =
                listOf(
                    ChromeHttpHeader("Sec-Fetch-Mode", "no-cors"),
                    ChromeHttpHeader("Sec-Fetch-Dest", "script"),
                ),
            body = ByteArray(0),
            bodyFraming = ChromeHttpBodyFraming.None,
        )

    private companion object {
        const val Session = "h19-ready-session"
        const val PolicyEpoch = 19L
        const val H20PolicyEpoch = 20L
        const val Now = 5_000L
        const val Origin = "https://shop.example"
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val FrameToken = "BBBBBBBBBBBBBBBBBBBBBB"
        val Challenge = "c".repeat(43)
    }
}
