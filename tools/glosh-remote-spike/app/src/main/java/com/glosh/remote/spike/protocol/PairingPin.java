package com.glosh.remote.spike.protocol;

import java.util.regex.Pattern;

public final class PairingPin {
    private static final Pattern SIX_DIGITS = Pattern.compile("[0-9]{6}");

    private PairingPin() {
    }

    public static boolean isValid(String value) {
        return value != null && SIX_DIGITS.matcher(value).matches();
    }
}
