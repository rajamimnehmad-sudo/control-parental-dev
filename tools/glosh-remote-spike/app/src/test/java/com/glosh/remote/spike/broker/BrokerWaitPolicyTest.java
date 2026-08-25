package com.glosh.remote.spike.broker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrokerWaitPolicyTest {
    @Test
    public void renewsPastOldFiveAttemptLimitWithinThirtyMinuteWindow() {
        FakeClock clock = new FakeClock();
        BrokerWaitPolicy policy = new BrokerWaitPolicy(clock);

        for (int attempt = 0; attempt < 12; attempt++) {
            assertTrue(policy.startNextRequest());
            assertTrue(policy.shouldRenew("expired"));
            clock.advance(60_000L);
        }
    }

    @Test
    public void stopsAfterThirtyMinuteOverallWindowAndResetStartsFresh() {
        FakeClock clock = new FakeClock();
        BrokerWaitPolicy policy = new BrokerWaitPolicy(clock);
        assertTrue(policy.startNextRequest());

        clock.advance(BrokerWaitPolicy.MAX_WAIT_MILLIS + 1L);
        assertFalse(policy.shouldRenew("expired"));
        assertFalse(policy.startNextRequest());

        policy.reset();
        assertTrue(policy.startNextRequest());
    }

    @Test
    public void onlyExpiryRenews() {
        BrokerWaitPolicy policy = new BrokerWaitPolicy();
        assertTrue(policy.startNextRequest());
        assertFalse(policy.shouldRenew("revoked"));
        assertFalse(policy.shouldRenew("claimed"));
        assertFalse(policy.shouldRenew("unknown"));
    }

    @Test
    public void transientFailuresBackOffAndSuccessResetsBudget() {
        FakeClock clock = new FakeClock();
        BrokerWaitPolicy policy = new BrokerWaitPolicy(clock);
        assertTrue(policy.startNextRequest());

        assertEquals(500L, policy.nextRetryDelayMillis());
        assertEquals(1_000L, policy.nextRetryDelayMillis());
        assertEquals(2_000L, policy.nextRetryDelayMillis());
        assertEquals(4_000L, policy.nextRetryDelayMillis());
        assertEquals(5_000L, policy.nextRetryDelayMillis());
        assertEquals(5_000L, policy.nextRetryDelayMillis());
        assertEquals(-1L, policy.nextRetryDelayMillis());

        policy.recordSuccess();
        assertEquals(500L, policy.nextRetryDelayMillis());
    }

    private static final class FakeClock implements BrokerWaitPolicy.Clock {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        void advance(long millis) {
            now += millis;
        }
    }
}
