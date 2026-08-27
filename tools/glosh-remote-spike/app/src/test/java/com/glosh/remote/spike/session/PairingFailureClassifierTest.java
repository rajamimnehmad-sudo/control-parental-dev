package com.glosh.remote.spike.session;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.ConnectException;

import org.junit.Test;

public final class PairingFailureClassifierTest {
    @Test
    public void staleEndpointWinsOverGenericError() {
        assertEquals(
                PairingFailureKind.ENDPOINT_CHANGED,
                PairingFailureClassifier.classify(new IOException("boom"), false));
    }

    @Test
    public void refusedSocketIsEndpointFailureNotBadPin() {
        assertEquals(
                PairingFailureKind.ENDPOINT_UNAVAILABLE,
                PairingFailureClassifier.classify(new ConnectException("Connection refused"), true));
    }

    @Test
    public void peerInfoFailureMeansPairingSecretWasRejected() {
        assertEquals(
                PairingFailureKind.PIN_REJECTED,
                PairingFailureClassifier.classify(
                        new IOException("Could not exchange peer info."), true));
    }

    @Test
    public void unknownFailureStaysAdbError() {
        assertEquals(
                PairingFailureKind.ADB_ERROR,
                PairingFailureClassifier.classify(new IllegalStateException("unexpected"), true));
    }
}
