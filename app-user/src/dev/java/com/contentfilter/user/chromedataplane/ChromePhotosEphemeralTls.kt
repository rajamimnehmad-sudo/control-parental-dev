package com.contentfilter.user.chromedataplane

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
import java.util.LinkedHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

internal data class ChromePhotosTlsServerMaterial(
    val hostname: String,
    val leafCertificate: X509Certificate,
    val sslContext: SSLContext,
)

internal class ChromePhotosEphemeralTlsMaterial private constructor(
    private val caKeyPair: KeyPair,
    private val caCertificate: X509Certificate,
    private val caName: X500Name,
    private val notBefore: Date,
    private val notAfter: Date,
    private val random: SecureRandom,
    private val maximumLeafCertificates: Int,
) : AutoCloseable {
    val caCertificateDer: ByteArray = caCertificate.encoded
    val caFingerprint: String = sha256(caCertificateDer)
    private val leafCache =
        object : LinkedHashMap<String, ChromePhotosTlsServerMaterial>(maximumLeafCertificates, LoadFactor, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ChromePhotosTlsServerMaterial>?,
            ): Boolean = size > maximumLeafCertificates
        }

    init {
        require(maximumLeafCertificates > 0)
    }

    @Synchronized
    fun serverMaterialFor(rawHostname: String): ChromePhotosTlsServerMaterial {
        val hostname = normalizeDnsHost(rawHostname)
        return leafCache[hostname] ?: createServerMaterial(hostname).also { leafCache[hostname] = it }
    }

    @Synchronized
    fun cachedLeafCount(): Int = leafCache.size

    @Synchronized
    fun cachedHosts(): Set<String> = leafCache.keys.toSet()

    @Synchronized
    override fun close() {
        leafCache.clear()
    }

    private fun createServerMaterial(hostname: String): ChromePhotosTlsServerMaterial {
        val leafKeyPair = rsaKeyPair(random)
        val extensions = JcaX509ExtensionUtils()
        val leafName = X500Name("CN=$hostname,O=Glosh DEV Lab")
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
                    GeneralNames(GeneralName(GeneralName.dNSName, hostname)),
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
        val leafCertificate = leafBuilder.signWith(caKeyPair)
        leafCertificate.verify(caCertificate.publicKey)

        val password = CharArray(0)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        keyStore.setKeyEntry(
            "$LeafAlias-$hostname",
            leafKeyPair.private,
            password,
            arrayOf(leafCertificate, caCertificate),
        )
        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, null, random)
            }
        return ChromePhotosTlsServerMaterial(
            hostname = hostname,
            leafCertificate = leafCertificate,
            sslContext = sslContext,
        )
    }

    companion object {
        internal fun create(
            now: Instant = Instant.now(),
            maximumLeafCertificates: Int = DefaultMaximumLeafCertificates,
        ): ChromePhotosEphemeralTlsMaterial {
            val random = SecureRandom()
            val caKeyPair = rsaKeyPair(random)
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
            val caCertificate = caBuilder.signWith(caKeyPair)
            return ChromePhotosEphemeralTlsMaterial(
                caKeyPair = caKeyPair,
                caCertificate = caCertificate,
                caName = caName,
                notBefore = notBefore,
                notAfter = notAfter,
                random = random,
                maximumLeafCertificates = maximumLeafCertificates,
            )
        }
    }
}

/** Creates an in-memory CA and bounded per-host leaf cache. Private keys are never persisted. */
internal object ChromePhotosEphemeralTls {
    fun create(
        now: Instant = Instant.now(),
        maximumLeafCertificates: Int = DefaultMaximumLeafCertificates,
    ): ChromePhotosEphemeralTlsMaterial =
        ChromePhotosEphemeralTlsMaterial.create(now, maximumLeafCertificates)
}

private fun JcaX509v3CertificateBuilder.signWith(signerKeyPair: KeyPair): X509Certificate {
    val signer =
        JcaContentSignerBuilder(SignatureAlgorithm)
            .build(signerKeyPair.private)
    return JcaX509CertificateConverter().getCertificate(build(signer))
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
private const val DefaultMaximumLeafCertificates = 8
private const val LoadFactor = 0.75f
