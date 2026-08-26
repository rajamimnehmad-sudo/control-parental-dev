package com.glosh.remote.spike.adb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class RemoteAccessPolicyTest {
    @Test
    public void validTransferAndAbsolutePathPass() {
        RemoteAccessPolicy.requireValidTransfer(
                "transfer_123",
                63_000_000,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        RemoteAccessPolicy.requireValidRemotePath("/data/local/tmp/Glosh.apk");
    }

    @Test
    public void transferLimitsFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                RemoteAccessPolicy.requireValidTransfer(
                        "bad/id",
                        1,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertThrows(IllegalArgumentException.class, () ->
                RemoteAccessPolicy.requireValidTransfer(
                        "valid",
                        RemoteAccessPolicy.MAX_TRANSFER_BYTES + 1,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    }

    @Test
    public void syncDelimiterAndRelativePathsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                RemoteAccessPolicy.requireValidRemotePath("data/local/tmp/a"));
        assertThrows(IllegalArgumentException.class, () ->
                RemoteAccessPolicy.requireValidRemotePath("/data/local/tmp/a,b"));
    }

    @Test
    public void sha256IsNormalized() {
        assertEquals("abcdef", RemoteAccessPolicy.normalizedSha256("ABCDEF"));
    }
}
