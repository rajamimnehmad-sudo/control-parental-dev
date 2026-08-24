package com.glosh.remote.spike.broker;

import com.glosh.remote.spike.protocol.JoinDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class SealedSession {
    public static final String PREFIX = "GLOSH-RENDEZVOUS-2";
    private static final String LEGACY_PREFIX = "GLOSH-RENDEZVOUS-1";

    private SealedSession() {
    }

    public static String open(
            RendezvousIdentity identity,
            String ciphertext,
            String expectedRequestId,
            String expectedNonce) throws GeneralSecurityException {
        byte[] plaintext = identity.decrypt(ciphertext);
        try {
            String value = new String(plaintext, StandardCharsets.UTF_8);
            String[] parts = value.split("\\n", 4);
            if (parts.length != 4 || !expectedRequestId.equals(parts[1])) {
                throw new GeneralSecurityException("Sealed session binding mismatch");
            }
            if (PREFIX.equals(parts[0])) {
                String expectedContext = sealContext(expectedRequestId, expectedNonce);
                if (!MessageDigest.isEqual(
                        expectedContext.getBytes(StandardCharsets.US_ASCII),
                        parts[2].getBytes(StandardCharsets.US_ASCII))) {
                    throw new GeneralSecurityException("Sealed session binding mismatch");
                }
            } else if (!LEGACY_PREFIX.equals(parts[0]) || !expectedNonce.equals(parts[2])) {
                throw new GeneralSecurityException("Sealed session binding mismatch");
            }
            JoinDescriptor descriptor = JoinDescriptor.parse(parts[3]);
            descriptor.destroy();
            return parts[3];
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid sealed session payload", error);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    static String sealContext(String requestId, String nonce) throws GeneralSecurityException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (requestId + ":" + nonce).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            Arrays.fill(digest, (byte) 0);
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new GeneralSecurityException("SHA-256 unavailable", error);
        }
    }
}
