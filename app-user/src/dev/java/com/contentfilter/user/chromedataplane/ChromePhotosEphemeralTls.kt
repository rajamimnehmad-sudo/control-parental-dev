package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

internal data class ChromePhotosEphemeralTlsMaterial(
    val caCertificateDer: ByteArray,
    val caFingerprint: String,
    val sslContext: SSLContext,
)

/** Creates an in-memory CA and leaf key. Private keys are never persisted. */
internal object ChromePhotosEphemeralTls {
    fun create(now: Instant = Instant.now()): ChromePhotosEphemeralTlsMaterial {
        val provider = BouncyCastleProvider()
        val random = SecureRandom()
        val caKeyPair = rsaKeyPair(random)
        val leafKeyPair = rsaKeyPair(random)
        val notBefore = Date.from(now.minus(ClockSkewMinutes, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(CertificateLifetimeHours, ChronoUnit.HOURS))
        val caName = X500Name("CN=Glosh Chrome Photos Ephemeral DEV CA,O=Glosh DEV Lab")
        val extensions = JcaX509ExtensionUtils()

        val caBuilder =
            JcaX509v3CertificateBuilder(
                caName,
                positiveSerial(random),
                notBefore,
                notAfter,
                caName,
                caKeyPair.public,
            ).apply {
                addExtension(Extension.basicConstraints, true, BasicConstraints(0))
                addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
                )
                addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extensions.createSubjectKeyIdentifier(caKeyPair.public),
                )
            }
        val caCertificate = caBuilder.signWith(caKeyPair, provider)

        val leafName = X500Name("CN=${ChromePhotosDataPlaneLabContract.FixtureHost},O=Glosh DEV Lab")
        val leafBuilder =
            JcaX509v3CertificateBuilder(
                caName,
                positiveSerial(random),
                notBefore,
                notAfter,
                leafName,
                leafKeyPair.public,
            ).apply {
                addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
                )
                addExtension(
                    Extension.extendedKeyUsage,
                    false,
                    ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
                )
                addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    GeneralNames(GeneralName(GeneralName.dNSName, ChromePhotosDataPlaneLabContract.FixtureHost)),
                )
                addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extensions.createSubjectKeyIdentifier(leafKeyPair.public),
                )
                addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extensions.createAuthorityKeyIdentifier(caCertificate),
                )
            }
        val leafCertificate = leafBuilder.signWith(caKeyPair, provider)
        leafCertificate.verify(caCertificate.publicKey)

        val password = CharArray(0)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        keyStore.setKeyEntry(
            LeafAlias,
            leafKeyPair.private,
            password,
            arrayOf(leafCertificate, caCertificate),
        )
        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
        val sslContext = SSLContext.getInstance("TLS").apply { init(keyManagerFactory.keyManagers, null, random) }
        val caDer = caCertificate.encoded
        return ChromePhotosEphemeralTlsMaterial(
            caCertificateDer = caDer,
            caFingerprint = sha256(caDer),
            sslContext = sslContext,
        )
    }

    private fun JcaX509v3CertificateBuilder.signWith(
        signerKeyPair: KeyPair,
        provider: BouncyCastleProvider,
    ): X509Certificate {
        val signer =
            JcaContentSignerBuilder(SignatureAlgorithm)
                .setProvider(provider)
                .build(signerKeyPair.private)
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(build(signer))
    }

    private fun rsaKeyPair(random: SecureRandom): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(RsaBits, random) }.generateKeyPair()

    private fun positiveSerial(random: SecureRandom): BigInteger =
        BigInteger(SerialBits, random)
            .abs()
            .add(BigInteger.ONE)

    private const val RsaBits = 2048
    private const val SerialBits = 128
    private const val ClockSkewMinutes = 5L
    private const val CertificateLifetimeHours = 12L
    private const val LeafAlias = "glosh-chrome-photos-leaf"
    private const val SignatureAlgorithm = "SHA256withRSA"
}
