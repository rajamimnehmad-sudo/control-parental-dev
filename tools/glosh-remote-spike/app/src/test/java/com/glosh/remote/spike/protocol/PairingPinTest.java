package com.glosh.remote.spike.protocol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingPinTest {
    @Test
    public void acceptsExactlySixAsciiDigits() {
        assertTrue(PairingPin.isValid("000000"));
        assertTrue(PairingPin.isValid("999999"));
        assertFalse(PairingPin.isValid("12345"));
        assertFalse(PairingPin.isValid("1234567"));
        assertFalse(PairingPin.isValid("１２３４５６"));
        assertFalse(PairingPin.isValid(null));
    }
}
