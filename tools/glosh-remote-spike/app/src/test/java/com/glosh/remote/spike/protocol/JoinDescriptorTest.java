package com.glosh.remote.spike.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

public class JoinDescriptorTest {
    @Test
    public void parsesValidDescriptor() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (255 - i);
        }
        String encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        String ws = "wss://small-owl.trycloudflare.com";
        String sid = "abcdefghijklmnopqrstuvwx";
        String uri = "gloshremote://join?v=1&url="
                + URLEncoder.encode(ws, StandardCharsets.UTF_8)
                + "&sid=" + sid
                + "&k=" + encodedKey;

        JoinDescriptor parsed = JoinDescriptor.parse(uri);

        assertEquals(ws, parsed.websocketUrl());
        assertEquals(sid, parsed.sessionId());
        assertArrayEquals(key, parsed.sessionKey());
    }

    @Test
    public void rejectsCleartextRelay() {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String uri = "gloshremote://join?v=1&url=ws%3A%2F%2Fexample.test&sid=abcdefghijklmnopqrstuvwx&k=" + key;
        try {
            JoinDescriptor.parse(uri);
            fail("Expected WSS validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void rejectsShortKey() {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        String uri = "gloshremote://join?v=1&url=wss%3A%2F%2Fexample.test&sid=abcdefghijklmnopqrstuvwx&k=" + key;
        try {
            JoinDescriptor.parse(uri);
            fail("Expected key length validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
