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

/** Persistent Wireless ADB identity plus a replaceable live connection. */
public final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static final long CERT_LIFETIME_MS = TimeUnit.DAYS.toMillis(3650);
    private static AdbConnectionManager instance;

    public static synchronized AdbConnectionManager getInstance(Context context) throws Exception {
        if (instance == null) {
            instance = new AdbConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    /** Drops only the live socket. The encrypted pairing identity intentionally survives. */
    public static synchronized void releaseConnection() {
        if (instance != null) {
            try {
                instance.disconnect();
            } catch (Exception ignored) {
                // Best effort. A later instance will reopen a fresh socket with the same key.
            }
            instance = null;
        }
    }

    /** Explicit destructive revocation hook; not used by the normal PIN-only lifecycle. */
    public static synchronized void forgetIdentity(Context context) throws Exception {
        if (instance != null) {
            try {
                instance.close();
            } finally {
                instance = null;
            }
        }
        AdbIdentityStore.delete(context.getApplicationContext());
    }

    /** Compatibility alias for dormant historical code; no longer destroys the persistent key. */
    @Deprecated
    public static synchronized void resetIdentity() {
        releaseConnection();
    }

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private AdbConnectionManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);

        AdbIdentityStore.Material stored = AdbIdentityStore.load(context);
        if (stored == null) {
            GeneratedIdentity generated = generateIdentity();
            AdbIdentityStore.save(context, generated.privateKey, generated.certificate);
            privateKey = generated.privateKey;
            certificate = generated.certificate;
        } else {
            privateKey = stored.privateKey;
            certificate = stored.certificate;
        }
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

    /** Reopens the current Wireless ADB TLS endpoint without changing the paired identity. */
    public synchronized boolean ensureConnected(Context context, long timeoutMillis) throws Exception {
        if (isConnected()) {
            return true;
        }
        try {
            disconnect();
        } catch (Exception ignored) {
            // The previous transport is already unusable. Continue with fresh mDNS discovery.
        }
        boolean connected = connectTls(context.getApplicationContext(), timeoutMillis);
        return connected || isConnected();
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
        Date notBefore = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1));
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
