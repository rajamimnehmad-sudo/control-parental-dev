package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PairingEndpointTrackerTest {
    @Test
    public void sameEndpointKeepsGeneration() {
        PairingEndpointTracker tracker = new PairingEndpointTracker();
        PairingEndpointTracker.Endpoint first = tracker.observe("192.0.2.10", 37123);
        PairingEndpointTracker.Endpoint second = tracker.observe("192.0.2.10", 37123);

        assertSame(first, second);
        assertTrue(tracker.isCurrent(first));
    }

    @Test
    public void changedPortInvalidatesOldSnapshot() {
        PairingEndpointTracker tracker = new PairingEndpointTracker();
        PairingEndpointTracker.Endpoint oldEndpoint = tracker.observe("192.0.2.10", 37123);
        PairingEndpointTracker.Endpoint newEndpoint = tracker.observe("192.0.2.10", 38111);

        assertFalse(tracker.isCurrent(oldEndpoint));
        assertTrue(tracker.isCurrent(newEndpoint));
    }

    @Test
    public void serviceLostInvalidatesEndpointEvenIfPortMayLaterRepeat() {
        PairingEndpointTracker tracker = new PairingEndpointTracker();
        PairingEndpointTracker.Endpoint oldEndpoint = tracker.observe("192.0.2.10", 37123);
        tracker.lost("192.0.2.10");

        assertNull(tracker.current());
        PairingEndpointTracker.Endpoint replacement = tracker.observe("192.0.2.10", 37123);
        assertFalse(tracker.isCurrent(oldEndpoint));
        assertTrue(tracker.isCurrent(replacement));
    }

    @Test
    public void lossForDifferentHostDoesNotDropCurrentEndpoint() {
        PairingEndpointTracker tracker = new PairingEndpointTracker();
        PairingEndpointTracker.Endpoint endpoint = tracker.observe("192.0.2.10", 37123);
        tracker.lost("192.0.2.11");

        assertTrue(tracker.isCurrent(endpoint));
    }
}
