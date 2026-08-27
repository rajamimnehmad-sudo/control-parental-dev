package com.glosh.remote.spike.session;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;

/** Converts libadb pairing failures into stable product-level causes without blaming every PIN. */
public final class PairingFailureClassifier {
    private static final String PEER_INFO_FAILURE = "Could not exchange peer info";

    private PairingFailureClassifier() {
    }

    public static PairingFailureKind classify(Throwable error, boolean endpointCurrent) {
        if (!endpointCurrent) {
            return PairingFailureKind.ENDPOINT_CHANGED;
        }

        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof ConnectException
                    || cursor instanceof NoRouteToHostException
                    || cursor instanceof SocketTimeoutException
                    || cursor instanceof SocketException
                    || cursor instanceof EOFException
                    || cursor instanceof SSLException) {
                return PairingFailureKind.ENDPOINT_UNAVAILABLE;
            }
            if (cursor instanceof IOException) {
                String message = cursor.getMessage();
                if (message != null && message.contains(PEER_INFO_FAILURE)) {
                    return PairingFailureKind.PIN_REJECTED;
                }
            }
            cursor = cursor.getCause();
        }
        return PairingFailureKind.ADB_ERROR;
    }
}
