package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromePhotosEphemeralTlsTest {
    @Test
    fun `ephemeral CA signs a hostname-valid TLS server without persisted private material`() {
        val material = ChromePhotosEphemeralTls.create(Instant.parse("2026-08-21T12:00:00Z"))
        val ca =
            CertificateFactory.getInstance("X.509").generateCertificate(
                ByteArrayInputStream(material.caCertificateDer),
            ) as X509Certificate

        assertTrue(ca.basicConstraints >= 0)
        assertEquals(64, material.caFingerprint.length)

        val server = material.sslContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        val accepted =
            executor.submit {
                server.accept().use { socket ->
                    (socket as SSLSocket).apply {
                        useClientMode = false
                        startHandshake()
                    }
                }
            }
        val raw = Socket(InetAddress.getLoopbackAddress(), server.localPort)
        val client =
            clientContext(ca).socketFactory.createSocket(
                raw,
                ChromePhotosDataPlaneLabContract.FixtureHost,
                server.localPort,
                true,
            ) as SSLSocket
        client.sslParameters = client.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        client.startHandshake()
        client.close()
        accepted.get()
        server.close()
        executor.shutdownNow()
    }

    private fun clientContext(ca: X509Certificate): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        trustStore.setCertificateEntry("ca", ca)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(trustStore)
        return SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }
    }
}
