package com.glosh.remote.spike.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ScreenAwakeLeaseTest {
    @Test
    public void leaseCoversTheSessionAndOneMinuteOfCleanupMargin() {
        assertEquals(
                TimeUnit.MINUTES.toMillis(31),
                ScreenAwakeLease.timeoutForSessionMinutes(30));
    }

    @Test
    public void invalidDurationFailsClosed() {
        try {
            ScreenAwakeLease.timeoutForSessionMinutes(0);
            fail("Expected invalid duration");
        } catch (IllegalArgumentException expected) {
            // PASS
        }
    }
}
