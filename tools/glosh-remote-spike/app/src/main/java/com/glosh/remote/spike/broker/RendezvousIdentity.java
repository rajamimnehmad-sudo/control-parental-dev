package com.glosh.remote.spike.broker;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.security.auth.DestroyFailedException;

public final class RendezvousIdentity {
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private PrivateKey privateKey;
    private byte[] publicKey;

    private RendezvousIdentity(KeyPair keyPair) {
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic().getEncoded();
    }

    public static RendezvousIdentity generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, new SecureRandom());
        return new RendezvousIdentity(generator.generateKeyPair());
    }

    public synchronized String encodedPublicKey() {
        ensureAlive();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
    }

    public synchronized byte[] decrypt(String encodedCiphertext) throws GeneralSecurityException {
        ensureAlive();
        byte[] ciphertext;
        try {
            ciphertext = Base64.getUrlDecoder().decode(encodedCiphertext);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid sealed session", error);
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
            return cipher.doFinal(ciphertext);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    public synchronized void destroy() {
        if (privateKey != null) {
            try {
                privateKey.destroy();
            } catch (DestroyFailedException ignored) {
                // Provider may not expose key zeroization; dropping the only reference is still fail-closed.
            }
        }
        privateKey = null;
        if (publicKey != null) {
            Arrays.fill(publicKey, (byte) 0);
            publicKey = null;
        }
    }

    private void ensureAlive() {
        if (privateKey == null || publicKey == null) {
            throw new IllegalStateException("Rendezvous identity destroyed");
        }
    }
}
