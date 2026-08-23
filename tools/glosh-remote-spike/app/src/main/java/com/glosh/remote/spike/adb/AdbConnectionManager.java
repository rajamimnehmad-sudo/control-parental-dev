package com.glosh.remote.spike.adb;

import android.content.Context;
import android.os.Build;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * Ephemeral ADB identity for REMOTE-INSTALL-CONNECTION-00.
 *
 * Nothing is written to disk: if this process dies or the session is revoked,
 * the private key disappears and Android must be paired again. That is the
 * intended product behavior for the temporary bootstrap bridge.
 */
public final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static final long CERT_LIFETIME_MS = TimeUnit.HOURS.toMillis(2);
    private static AdbConnectionManager instance;

    public static synchronized AdbConnectionManager getInstance(Context context) throws Exception {
        if (instance == null) {
            instance = new AdbConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void resetIdentity() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {
                try {
                    instance.disconnect();
                } catch (Exception ignoredAgain) {
                    // Best effort. Dropping the singleton still makes the identity unreachable.
                }
            }
            instance = null;
        }
    }

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private AdbConnectionManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);

        GeneratedIdentity generated = generateIdentity();
        privateKey = generated.privateKey;
        certificate = generated.certificate;
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "Glosh Remote";
    }

    private static GeneratedIdentity generateIdentity() throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        KeyPair pair = generator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        PrivateKey privateKey = pair.getPrivate();

        String algorithmName = "SHA512withRSA";
        X500Name x500Name = new X500Name("CN=Glosh Remote");
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + CERT_LIFETIME_MS);

        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set(
                "SubjectKeyIdentifier",
                new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
        extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));

        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(random.nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithmName)));
        info.set("subject", new CertificateSubjectName(x500Name));
        info.set("key", new CertificateX509Key(publicKey));
        info.set("validity", new CertificateValidity(notBefore, notAfter));
        info.set("issuer", new CertificateIssuerName(x500Name));
        info.set("extensions", extensions);

        X509CertImpl certificate = new X509CertImpl(info);
        certificate.sign(privateKey, algorithmName);
        return new GeneratedIdentity(privateKey, certificate);
    }

    private static final class GeneratedIdentity {
        private final PrivateKey privateKey;
        private final Certificate certificate;

        private GeneratedIdentity(PrivateKey privateKey, Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
