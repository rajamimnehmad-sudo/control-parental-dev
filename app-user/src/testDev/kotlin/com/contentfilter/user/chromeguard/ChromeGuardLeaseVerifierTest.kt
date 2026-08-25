package com.contentfilter.user.chromeguard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChromeGuardLeaseVerifierTest {
    @Test
    fun `valid current lease is accepted`() {
        assertEquals(ChromeGuardLeaseVerification.Accepted, verify())
    }

    @Test
    fun `expired lease is rejected`() {
        assertRejected("lease_expired", verify(lease = lease(expiresAt = Now)))
    }

    @Test
    fun `stale generation session nonce and boot are rejected`() {
        assertRejected("generation_stale", verify(lease = lease(generation = 8L)))
        assertRejected("session_stale", verify(lease = lease(sessionId = "old")))
        assertRejected("nonce_stale", verify(lease = lease(nonce = "old")))
        assertRejected("boot_stale", verify(lease = lease(bootMarker = 40L)))
    }

    @Test
    fun `wrong caller is rejected before lease identity`() {
        assertRejected("wrong_caller", verify(callerAuthorized = false))
    }

    @Test
    fun `duplicate and reordered heartbeat are rejected`() {
        assertRejected("heartbeat_replayed", verify(lease = lease(sequence = 4L)))
        assertRejected("heartbeat_replayed", verify(lease = lease(sequence = 3L)))
    }

    @Test
    fun `health dependencies fail closed with stable reasons`() {
        assertRejected("transport_lost", verify(lease = lease(health = healthy(transport = false))))
        assertRejected("vpn_lost", verify(lease = lease(health = healthy(vpn = false))))
        assertRejected("proxy_lost", verify(lease = lease(health = healthy(proxy = false))))
        assertRejected("policy_lost", verify(lease = lease(health = healthy(policy = false))))
        assertRejected("gloshia_lost", verify(lease = lease(health = healthy(gloshia = false))))
        assertRejected("accessibility_lost", verify(lease = lease(health = healthy(accessibility = false))))
        assertRejected("bootstrap_invalid", verify(lease = lease(health = healthy(bootstrap = false))))
    }

    @Test
    fun `ttl and runtime generation are bounded`() {
        assertRejected(
            "ttl_invalid",
            verify(lease = lease(issuedAt = Now - 2_000L, expiresAt = Now + 1L)),
        )
        assertRejected("runtime_generation_invalid", verify(lease = lease(transportGeneration = 0L)))
        assertRejected("runtime_generation_invalid", verify(lease = lease(proxyGeneration = 0L)))
    }

    private fun verify(
        lease: ChromeGuardLease = lease(),
        callerAuthorized: Boolean = true,
    ) = ChromeGuardLeaseVerifier.verify(
        lease = lease,
        expected = expected(),
        currentBootMarker = BootMarker,
        nowElapsed = Now,
        callerAuthorized = callerAuthorized,
    )

    private fun expected() =
        ChromeGuardExpectedSession(
            protectionGeneration = 7L,
            sessionId = "session-current",
            mainProcessNonce = "nonce-current",
            bootMarker = BootMarker,
            bootstrapGeneration = 1,
            lastHeartbeatSequence = 4L,
        )

    private fun lease(
        generation: Long = 7L,
        sessionId: String = "session-current",
        nonce: String = "nonce-current",
        bootMarker: Long = BootMarker,
        sequence: Long = 5L,
        issuedAt: Long = Now,
        expiresAt: Long = Now + ChromeGuardContract.LeaseTtlMillis,
        transportGeneration: Long = 7L,
        proxyGeneration: Long = 7L,
        health: ChromeGuardHealth = healthy(),
    ) = ChromeGuardLease(
        protectionGeneration = generation,
        sessionId = sessionId,
        mainProcessNonce = nonce,
        bootMarker = bootMarker,
        heartbeatSequence = sequence,
        issuedAtElapsedRealtime = issuedAt,
        expiresAtElapsedRealtime = expiresAt,
        transportGeneration = transportGeneration,
        proxyGeneration = proxyGeneration,
        bootstrapGeneration = 1,
        health = health,
    )

    private fun healthy(
        vpn: Boolean = true,
        transport: Boolean = true,
        proxy: Boolean = true,
        policy: Boolean = true,
        gloshia: Boolean = true,
        accessibility: Boolean = true,
        bootstrap: Boolean = true,
    ) = ChromeGuardHealth(vpn, transport, proxy, policy, gloshia, accessibility, bootstrap)

    private fun assertRejected(
        reason: String,
        result: ChromeGuardLeaseVerification,
    ) {
        assertEquals(reason, assertIs<ChromeGuardLeaseVerification.Rejected>(result).reason)
    }

    private companion object {
        const val Now = 10_000L
        const val BootMarker = 41L
    }
}
