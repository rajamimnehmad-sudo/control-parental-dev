package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosHttpsProxyTlsLoggingTest {
    @Test
    fun `upstream handshake failure is logged with request correlation and safe metadata`() {
        val fixture = FakeFixtureSource()
        val warnings = mutableListOf<String>()
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                upstream = FailingTlsUpstream(),
                transformer = chromePhotosDeterministicTransformer(fixture),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = warnings::add,
            )

        val disposition =
            proxy.use {
                it.serveRealRequest(
                    host = "example.com",
                    request =
                        ChromePhotosProxyRequest(
                            method = "GET",
                            target = "/",
                            headers = listOf(ChromeHttpHeader("Host", "example.com")),
                        ),
                    clientProtocol = "http/1.1",
                    output = ByteArrayOutputStream(),
                    correlationId = "c9-r2",
                )
            }

        assertEquals(ChromeHttpConnectionDisposition.Close, disposition)
        val tlsLine = warnings.single { it.contains("phase=tls_failed") }
        assertTrue(tlsLine.contains("side=upstream"))
        assertTrue(tlsLine.contains("stage=handshake"))
        assertTrue(tlsLine.contains("correlationId=c9-r2"))
        assertTrue(tlsLine.contains("host=example.com"))
        assertTrue(tlsLine.contains("authority=example.com:443"))
        assertTrue(tlsLine.contains("sni=example.com"))
        assertTrue(tlsLine.contains("error=SSLHandshakeException"))
        assertTrue(tlsLine.contains("rootCause=CertificateException"))
        assertFalse(tlsLine.contains("secret-upstream-detail"))
        assertFalse(tlsLine.contains("secret-certificate-detail"))
    }

    private class FailingTlsUpstream : ChromePhotosUpstream {
        override fun execute(
            host: String,
            request: ChromePhotosProxyRequest,
        ): ChromePhotosUpstreamExchange {
            val certificate = CertificateException("secret-certificate-detail")
            val handshake = SSLHandshakeException("secret-upstream-detail").apply { initCause(certificate) }
            throw IOException("wrapper detail must stay private").apply { initCause(handshake) }
        }
    }

    private class FakeFixtureSource : ChromePhotosFixtureSource {
        override val safeImageBytes = "safe".toByteArray()
        override val sentinelImageBytes = "block".toByteArray()
        override val placeholderImageBytes = "placeholder".toByteArray()

        override fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse =
            ChromePhotosFixtureResponse(
                resourceId = "unused",
                contentType = "text/plain",
                originalBytes = "fixture".toByteArray(),
            )
    }
}
