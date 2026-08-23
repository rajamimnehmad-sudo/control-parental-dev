package com.glosh.remote.spike.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SessionCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;

    private SessionCrypto() {
    }

    public static final class Box {
        private final String nonce;
        private final String ciphertext;

        public Box(String nonce, String ciphertext) {
            this.nonce = nonce;
            this.ciphertext = ciphertext;
        }

        public String nonce() {
            return nonce;
        }

        public String ciphertext() {
            return ciphertext;
        }
    }

    public static Box encrypt(byte[] key, byte[] plaintext, String aad) throws GeneralSecurityException {
        requireKey(key);
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new Box(b64(nonce), b64(ciphertext));
    }

    public static byte[] decrypt(byte[] key, String nonce, String ciphertext, String aad)
            throws GeneralSecurityException {
        requireKey(key);
        byte[] nonceBytes = b64decode(nonce);
        if (nonceBytes.length != GCM_NONCE_BYTES) {
            throw new GeneralSecurityException("Nonce inválido.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonceBytes));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(b64decode(ciphertext));
    }

    public static String hmac(byte[] key, String value) throws GeneralSecurityException {
        requireKey(key);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return b64(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(b64decode(left), b64decode(right));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] b64decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static void requireKey(byte[] key) throws GeneralSecurityException {
        if (key == null || key.length != 32) {
            throw new GeneralSecurityException("La clave de sesión debe tener 256 bits.");
        }
    }
}
