package com.glosh.remote.spike.session;

/** User-safe classification of failures that happen while submitting a Wireless ADB PIN. */
public enum PairingFailureKind {
    NONE,
    PIN_REJECTED,
    ENDPOINT_CHANGED,
    ENDPOINT_UNAVAILABLE,
    ADB_ERROR
}
