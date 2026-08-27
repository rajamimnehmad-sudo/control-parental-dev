package com.contentfilter.user.chromedataplane

import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeProxyTlsDiagnosticsTest {
    @Test
    fun `handshake failure is classifiable without logging exception messages`() {
        val rootCause = CertificateException("sensitive-certificate-detail")
        val handshake = SSLHandshakeException("private-user-url=https://secret.example/path").apply { initCause(rootCause) }
        val wrapper = IOException("request metadata must not be logged").apply { initCause(handshake) }
        val context =
            ChromeProxyTlsContext(
                side = ChromeProxyTlsSide.Upstream,
                stage = "handshake",
                correlationId = "c42-r7",
                host = "example.com",
                authority = "example.com:443",
                sni = "example.com",
            )

        val failure = ChromeProxyTlsDiagnostics.classify(wrapper)
        val line = failure?.logLine(context).orEmpty()

        assertEquals("SSLHandshakeException", failure?.errorClass)
        assertEquals("CertificateException", failure?.rootCauseClass)
        assertTrue(line.contains("phase=tls_failed"))
        assertTrue(line.contains("side=upstream"))
        assertTrue(line.contains("stage=handshake"))
        assertTrue(line.contains("correlationId=c42-r7"))
        assertTrue(line.contains("host=example.com"))
        assertTrue(line.contains("authority=example.com:443"))
        assertTrue(line.contains("sni=example.com"))
        assertTrue(line.contains("causeChain=IOException>SSLHandshakeException>CertificateException"))
        assertFalse(line.contains("sensitive-certificate-detail"))
        assertFalse(line.contains("secret.example"))
        assertFalse(line.contains("request metadata"))
    }

    @Test
    fun `client side explicitly reports SNI as not observed`() {
        val handshake = SSLHandshakeException("do not log this")
        val line =
            ChromeProxyTlsDiagnostics.logLine(
                context =
                    ChromeProxyTlsContext(
                        side = ChromeProxyTlsSide.Client,
                        stage = "handshake",
                        correlationId = "c8",
                        host = "example.com",
                        authority = "example.com:443",
                        sni = null,
                    ),
                error = handshake,
            ).orEmpty()

        assertTrue(line.contains("side=client"))
        assertTrue(line.contains("correlationId=c8"))
        assertTrue(line.contains("sni=not_observed"))
        assertFalse(line.contains("do not log this"))
    }

    @Test
    fun `non TLS failure is not misclassified`() {
        assertNull(ChromeProxyTlsDiagnostics.classify(IOException("ordinary I O failure")))
    }
}
