package com.glosh.remote.spike.broker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.junit.Test;

public class SealedSessionTest {
    private static final String REQUEST_ID = "request_abcdefghijklmnop";
    private static final String NONCE = "nonce_abcdefghijklmnopqr";
    private static final String DESCRIPTOR =
            "gloshremote://join?v=1&url=wss%3A%2F%2Frelay.example.test"
                    + "&sid=abcdefghijklmnopqrstuvwx"
                    + "&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    @Test
    public void rsaOaepDeliveryIsBoundAndNotPlaintext() throws Exception {
        RendezvousIdentity identity = RendezvousIdentity.generate();
        String ciphertext = seal(identity, REQUEST_ID, NONCE, DESCRIPTOR);
        assertFalse(ciphertext.contains(DESCRIPTOR));
        assertEquals(DESCRIPTOR, SealedSession.open(identity, ciphertext, REQUEST_ID, NONCE));
        identity.destroy();
    }

    @Test
    public void replayForAnotherRequestIsRejected() throws Exception {
        RendezvousIdentity identity = RendezvousIdentity.generate();
        String ciphertext = seal(identity, REQUEST_ID, NONCE, DESCRIPTOR);
        try {
            SealedSession.open(identity, ciphertext, "request_other_abcdefghijk", NONCE);
            fail("Expected request binding failure");
        } catch (Exception expected) {
            // expected
        } finally {
            identity.destroy();
        }
    }

    private static String seal(
            RendezvousIdentity identity,
            String requestId,
            String nonce,
            String descriptor) throws Exception {
        byte[] encoded = Base64.getUrlDecoder().decode(identity.encodedPublicKey());
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        String payload = SealedSession.PREFIX + "\n" + requestId + "\n" + nonce + "\n" + descriptor;
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT));
        byte[] sealed = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sealed);
    }
}
