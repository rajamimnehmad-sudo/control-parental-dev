package com.glosh.remote.spike.guide.autopilot;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WirelessDirectRoutePolicyTest {
    @Test
    public void repeatedSameDeveloperScreenNeverRelaunchesDirectRoute() {
        WirelessDirectRoutePolicy policy = new WirelessDirectRoutePolicy();
        policy.markDirectAttempt();

        assertEquals(
                WirelessDirectRoutePolicy.Decision.WAIT_FOR_USER,
                policy.onDeveloperOptions("same"));
        assertEquals(
                WirelessDirectRoutePolicy.Decision.WAIT_FOR_USER,
                policy.onDeveloperOptions("same"));
        assertEquals(
                WirelessDirectRoutePolicy.Decision.WAIT_FOR_USER,
                policy.onDeveloperOptions("same"));
        assertEquals(1, policy.attempts());
    }

    @Test
    public void oneUserDrivenStateChangeAllowsExactlyOneRetry() {
        WirelessDirectRoutePolicy policy = new WirelessDirectRoutePolicy();
        policy.markDirectAttempt();

        assertEquals(
                WirelessDirectRoutePolicy.Decision.WAIT_FOR_USER,
                policy.onDeveloperOptions("developer-off"));
        assertEquals(
                WirelessDirectRoutePolicy.Decision.RETRY_DIRECT_ONCE,
                policy.onDeveloperOptions("developer-on"));

        policy.markDirectAttempt();
        assertEquals(2, policy.attempts());
        assertEquals(
                WirelessDirectRoutePolicy.Decision.VISUAL_FALLBACK,
                policy.onDeveloperOptions("developer-on"));
        assertEquals(
                WirelessDirectRoutePolicy.Decision.VISUAL_FALLBACK,
                policy.onDeveloperOptions("another-change"));
    }

    @Test
    public void unexpectedDeveloperScreenWithoutDirectAttemptIsVisualFallback() {
        WirelessDirectRoutePolicy policy = new WirelessDirectRoutePolicy();
        assertEquals(
                WirelessDirectRoutePolicy.Decision.VISUAL_FALLBACK,
                policy.onDeveloperOptions("anything"));
    }

    @Test
    public void resetStartsAFreshBoundedSession() {
        WirelessDirectRoutePolicy policy = new WirelessDirectRoutePolicy();
        policy.markDirectAttempt();
        policy.onDeveloperOptions("a");
        policy.onDeveloperOptions("b");
        policy.markDirectAttempt();

        policy.reset();
        assertEquals(0, policy.attempts());
        policy.markDirectAttempt();
        assertEquals(
                WirelessDirectRoutePolicy.Decision.WAIT_FOR_USER,
                policy.onDeveloperOptions("fresh"));
    }
}
