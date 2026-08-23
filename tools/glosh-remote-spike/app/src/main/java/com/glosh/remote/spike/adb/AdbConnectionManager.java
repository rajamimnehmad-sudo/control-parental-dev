package com.glosh.remote.spike.adb;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Random;
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
 * ADB identity used only by the temporary Glosh Remote spike.
 *
 * The key never leaves app-private storage. Resetting the identity deletes both
 * the private key and certificate so an old Android pairing cannot be reused.
 */
public final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static final String KEY_FILE = "remote-adb-private.key";
    private static final String CERT_FILE = "remote-adb-cert.pem";
    private static final long CERT_LIFETIME_MS = TimeUnit.HOURS.toMillis(24);

    private static AdbConnectionManager instance;

    public static synchronized AdbConnectionManager getInstance(Context context) throws Exception {
        if (instance == null) {
            instance = new AdbConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void resetIdentity(Context context) {
        if (instance != null) {
            try {
                instance.disconnect();
            } catch (IOException ignored) {
            }
            instance = null;
        }
        new File(context.getFilesDir(), KEY_FILE).delete();
        new File(context.getFilesDir(), CERT_FILE).delete();
    }

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private AdbConnectionManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);

        PrivateKey storedKey = readPrivateKey(context);
        Certificate storedCert = readCertificate(context);
        if (storedKey == null || storedCert == null) {
            new File(context.getFilesDir(), KEY_FILE).delete();
            new File(context.getFilesDir(), CERT_FILE).delete();
            GeneratedIdentity generated = generateIdentity();
            privateKey = generated.privateKey;
            certificate = generated.certificate;
            writePrivateKey(context, privateKey);
            writeCertificate(context, certificate);
        } else {
            privateKey = storedKey;
            certificate = storedCert;
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
        return "Glosh Remote Spike";
    }

    private static GeneratedIdentity generateIdentity() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair pair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        PrivateKey privateKey = pair.getPrivate();

        String algorithmName = "SHA512withRSA";
        X500Name x500Name = new X500Name("CN=Glosh Remote Spike");
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + CERT_LIFETIME_MS);

        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set(
                "SubjectKeyIdentifier",
                new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
        extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));

        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
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

    private static PrivateKey readPrivateKey(Context context) {
        File file = new File(context.getFilesDir(), KEY_FILE);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = readAll(input);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Certificate readCertificate(Context context) {
        File file = new File(context.getFilesDir(), CERT_FILE);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writePrivateKey(Context context, PrivateKey key) throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), KEY_FILE))) {
            output.write(key.getEncoded());
        }
    }

    private static void writeCertificate(Context context, Certificate certificate) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
        String pem = "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), CERT_FILE))) {
            output.write(pem.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static byte[] readAll(FileInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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
