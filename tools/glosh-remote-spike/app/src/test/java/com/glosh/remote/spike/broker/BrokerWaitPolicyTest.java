package com.glosh.remote.spike.broker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrokerWaitPolicyTest {
    @Test
    public void renewsExpiredRequestsWithinABoundedWindow() {
        BrokerWaitPolicy policy = new BrokerWaitPolicy();

        for (int attempt = 1; attempt <= BrokerWaitPolicy.MAX_REQUEST_ATTEMPTS; attempt++) {
            assertTrue(policy.startNextRequest());
            assertTrue(policy.shouldRenew("expired")
                    == (attempt < BrokerWaitPolicy.MAX_REQUEST_ATTEMPTS));
        }

        assertFalse(policy.startNextRequest());
    }

    @Test
    public void onlyExpiryRenewsAndResetAllowsANewWait() {
        BrokerWaitPolicy policy = new BrokerWaitPolicy();
        assertTrue(policy.startNextRequest());
        assertFalse(policy.shouldRenew("revoked"));
        assertFalse(policy.shouldRenew("claimed"));
        assertFalse(policy.shouldRenew("unknown"));

        policy.reset();
        assertTrue(policy.startNextRequest());
    }
}
