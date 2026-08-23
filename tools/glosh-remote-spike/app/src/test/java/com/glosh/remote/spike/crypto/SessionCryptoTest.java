package com.glosh.remote.spike.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.junit.Test;

public class SessionCryptoTest {
    @Test
    public void encryptDecryptRoundTrip() throws Exception {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        byte[] plaintext = "hello glosh remote".getBytes(StandardCharsets.UTF_8);
        String aad = "sid:server:1";

        SessionCrypto.Box box = SessionCrypto.encrypt(key, plaintext, aad);
        byte[] decrypted = SessionCrypto.decrypt(key, box.nonce(), box.ciphertext(), aad);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    public void decryptsPythonKnownVector() throws Exception {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        String aad = "abcdefghijklmnopqrstuvwx:server:1";
        String nonce = "AAECAwQFBgcICQoL";
        String ciphertext = "PCC9cquB4CGvIvjm3IgWCaH6pUaVCioZSxOs4T9TIsZkY9reg-Nz-wDNEIOqvQpIhzcHryfcBoE3NKb6D0_mKRJLmgLt";
        byte[] expected = "{\"kind\":\"command\",\"requestId\":\"test\",\"action\":\"ping\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected, SessionCrypto.decrypt(key, nonce, ciphertext, aad));
    }

    @Test
    public void wrongAadIsRejected() throws Exception {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        SessionCrypto.Box box = SessionCrypto.encrypt(
                key,
                "payload".getBytes(StandardCharsets.UTF_8),
                "sid:server:2");

        try {
            SessionCrypto.decrypt(key, box.nonce(), box.ciphertext(), "sid:server:3");
            fail("Expected authentication failure");
        } catch (GeneralSecurityException expected) {
            // expected
        }
    }

    @Test
    public void hmacComparisonIsSafeForInvalidInput() throws Exception {
        byte[] key = new byte[32];
        String proof = SessionCrypto.hmac(key, "challenge");
        assertTrue(SessionCrypto.constantTimeEquals(proof, proof));
        assertFalse(SessionCrypto.constantTimeEquals(proof, "not-base64***"));
    }
}
