package com.glosh.remote.spike.broker;

import com.glosh.remote.spike.protocol.JoinDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class SealedSession {
    public static final String PREFIX = "GLOSH-RENDEZVOUS-1";

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
            if (parts.length != 4
                    || !PREFIX.equals(parts[0])
                    || !expectedRequestId.equals(parts[1])
                    || !expectedNonce.equals(parts[2])) {
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
}
