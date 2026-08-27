package com.glosh.remote.spike.adb;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private persistent ADB identity, wrapped at rest by a non-exportable Android Keystore key. */
final class AdbIdentityStore {
    private static final String PREFS = "glosh_remote_adb_identity_v1";
    private static final String FIELD_PRIVATE_KEY = "private_key";
    private static final String FIELD_CERTIFICATE = "certificate";
    private static final String WRAP_KEY_ALIAS = "glosh_remote_adb_identity_wrap_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private AdbIdentityStore() {
    }

    static synchronized Material load(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String privateValue = prefs.getString(FIELD_PRIVATE_KEY, null);
        String certificateValue = prefs.getString(FIELD_CERTIFICATE, null);
        if (privateValue == null || certificateValue == null) {
            if (privateValue != null || certificateValue != null) {
                prefs.edit().clear().commit();
            }
            return null;
        }

        SecretKey wrappingKey = getOrCreateWrappingKey();
        byte[] privateBytes = null;
        byte[] certificateBytes = null;
        try {
            privateBytes = decrypt(wrappingKey, privateValue, FIELD_PRIVATE_KEY);
            certificateBytes = decrypt(wrappingKey, certificateValue, FIELD_CERTIFICATE);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            Certificate certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificateBytes));
            return new Material(privateKey, certificate);
        } catch (Exception corrupted) {
            // The wrapped record is unusable (for example after a keystore reset). Drop only the
            // app-private ciphertext; the caller will generate one fresh identity and pair once.
            prefs.edit().clear().commit();
            return null;
        } finally {
            if (privateBytes != null) {
                Arrays.fill(privateBytes, (byte) 0);
            }
            if (certificateBytes != null) {
                Arrays.fill(certificateBytes, (byte) 0);
            }
        }
    }

    static synchronized void save(
            Context context,
            PrivateKey privateKey,
            Certificate certificate) throws Exception {
        byte[] privateBytes = privateKey.getEncoded();
        byte[] certificateBytes = certificate.getEncoded();
        if (privateBytes == null || certificateBytes == null) {
            throw new IOException("ADB identity is not encodable.");
        }
        try {
            SecretKey wrappingKey = getOrCreateWrappingKey();
            String wrappedPrivate = encrypt(wrappingKey, privateBytes, FIELD_PRIVATE_KEY);
            String wrappedCertificate = encrypt(wrappingKey, certificateBytes, FIELD_CERTIFICATE);
            boolean committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(FIELD_PRIVATE_KEY, wrappedPrivate)
                    .putString(FIELD_CERTIFICATE, wrappedCertificate)
                    .commit();
            if (!committed) {
                throw new IOException("Could not persist ADB identity.");
            }
        } finally {
            Arrays.fill(privateBytes, (byte) 0);
            Arrays.fill(certificateBytes, (byte) 0);
        }
    }

    static synchronized void delete(Context context) throws Exception {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(WRAP_KEY_ALIAS)) {
            keyStore.deleteEntry(WRAP_KEY_ALIAS);
        }
    }

    private static SecretKey getOrCreateWrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(WRAP_KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(WRAP_KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(SecretKey key, byte[] plaintext, String label) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key, new SecureRandom());
        cipher.updateAAD(label.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length == 0 || iv.length > 255) {
            throw new IOException("Invalid GCM IV.");
        }
        ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
        packed.put((byte) iv.length).put(iv).put(ciphertext);
        Arrays.fill(ciphertext, (byte) 0);
        return Base64.encodeToString(packed.array(), Base64.NO_WRAP);
    }

    private static byte[] decrypt(SecretKey key, String encoded, String label) throws Exception {
        byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
        try {
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            int ivLength = buffer.get() & 0xff;
            if (ivLength <= 0 || ivLength > buffer.remaining()) {
                throw new IOException("Invalid wrapped ADB identity.");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            try {
                Cipher cipher = Cipher.getInstance(CIPHER);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                cipher.updateAAD(label.getBytes(StandardCharsets.UTF_8));
                return cipher.doFinal(ciphertext);
            } finally {
                Arrays.fill(ciphertext, (byte) 0);
            }
        } finally {
            Arrays.fill(packed, (byte) 0);
        }
    }

    static final class Material {
        final PrivateKey privateKey;
        final Certificate certificate;

        Material(PrivateKey privateKey, Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
