package com.contentfilter.user.chromedataplane

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
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChromePhotosEphemeralTlsTest {
    @Test
    fun `ephemeral CA signs exact hostname leaves with a shared session CA`() {
        val material = ChromePhotosEphemeralTls.create(Instant.now())
        val ca =
            CertificateFactory.getInstance("X.509").generateCertificate(
                ByteArrayInputStream(material.caCertificateDer),
            ) as X509Certificate

        assertTrue(ca.basicConstraints >= 0)
        assertEquals(64, material.caFingerprint.length)

        val httpBingo = material.serverMaterialFor(ChromePhotosRealWebLabConfig.HttpBingoHost)
        val google = material.serverMaterialFor(ChromePhotosRealWebLabConfig.GoogleStaticHost)

        assertTrue(httpBingo.leafCertificate.sanDnsNames().contains(ChromePhotosRealWebLabConfig.HttpBingoHost))
        assertTrue(google.leafCertificate.sanDnsNames().contains(ChromePhotosRealWebLabConfig.GoogleStaticHost))
        assertNotEquals(httpBingo.leafCertificate.serialNumber, google.leafCertificate.serialNumber)
        httpBingo.leafCertificate.verify(ca.publicKey)
        google.leafCertificate.verify(ca.publicKey)
        assertSame(httpBingo, material.serverMaterialFor(ChromePhotosRealWebLabConfig.HttpBingoHost))

        val server = httpBingo.sslContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress())
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
                ChromePhotosRealWebLabConfig.HttpBingoHost,
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

    @Test
    fun `leaf cache is bounded and reset removes all per-host material`() {
        val material = ChromePhotosEphemeralTls.create(maximumLeafCertificates = 2)

        material.serverMaterialFor(ChromePhotosRealWebLabConfig.HttpBingoHost)
        material.serverMaterialFor(ChromePhotosRealWebLabConfig.GoogleStaticHost)
        material.serverMaterialFor(ChromePhotosRealWebLabConfig.GitHubHost)

        assertEquals(2, material.cachedLeafCount())
        assertEquals(
            setOf(ChromePhotosRealWebLabConfig.GoogleStaticHost, ChromePhotosRealWebLabConfig.GitHubHost),
            material.cachedHosts(),
        )
        material.close()
        assertEquals(0, material.cachedLeafCount())
    }

    private fun clientContext(ca: X509Certificate): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        trustStore.setCertificateEntry("ca", ca)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(trustStore)
        return SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }
    }

    private fun X509Certificate.sanDnsNames(): Set<String> =
        subjectAlternativeNames
            .orEmpty()
            .filter { entry -> entry.firstOrNull() == 2 }
            .mapNotNullTo(mutableSetOf()) { entry -> entry.getOrNull(1) as? String }
}
