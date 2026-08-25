package com.contentfilter.user.chromeguard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeGuardCoordinatorTest {
    @Test
    fun `no lease defaults to verified suspension`() {
        val fixture = fixture()

        assertTrue(fixture.coordinator.initialize())
        assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
        assertEquals(0, fixture.suspension.releaseCalls)
    }

    @Test
    fun `current lease releases and expiry suspends`() {
        val fixture = fixture()
        val generation = fixture.begin()

        assertEquals(ChromeGuardLeaseVerification.Accepted, fixture.heartbeat(generation))
        assertEquals(ChromeGuardState.Released, fixture.coordinator.snapshot().state)
        assertFalse(fixture.coordinator.expireIfNeeded(Now + ChromeGuardContract.LeaseTtlMillis - 1L))
        assertTrue(fixture.coordinator.expireIfNeeded(Now + ChromeGuardContract.LeaseTtlMillis))
        assertEquals("main_process_lost", fixture.coordinator.snapshot().lastReason)
        assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
    }

    @Test
    fun `suspension failure never allows release`() {
        val fixture = fixture(suspendSucceeds = false)

        assertNull(fixture.coordinator.beginSession(fixture.request(), callerAuthorized = true))
        assertEquals(ChromeGuardState.Unverified, fixture.coordinator.snapshot().state)
        assertEquals(0, fixture.suspension.releaseCalls)
    }

    @Test
    fun `release verification failure resuspends and invalidates lease`() {
        val fixture = fixture(releaseSucceeds = false)
        val generation = fixture.begin()
        val suspendsBeforeHeartbeat = fixture.suspension.suspendCalls

        val result = fixture.heartbeat(generation)

        assertTrue(result is ChromeGuardLeaseVerification.Rejected)
        assertEquals("suspension_unverified", fixture.coordinator.snapshot().lastReason)
        assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
        assertEquals(suspendsBeforeHeartbeat + 1, fixture.suspension.suspendCalls)
        assertEquals(0L, fixture.coordinator.snapshot().protectionGeneration)
    }

    @Test
    fun `release and fallback suspension failure remains unverified`() {
        val fixture = fixture(suspendSucceeds = false, releaseSucceeds = false)
        // Open a session while suspension authority is healthy, then fail both DPM operations.
        fixture.suspension.suspendSucceeds = true
        val generation = fixture.begin()
        fixture.suspension.suspendSucceeds = false

        val result = fixture.heartbeat(generation)

        assertTrue(result is ChromeGuardLeaseVerification.Rejected)
        assertEquals(ChromeGuardState.Unverified, fixture.coordinator.snapshot().state)
        assertEquals("suspension_unverified", fixture.coordinator.snapshot().lastReason)
    }

    @Test
    fun `wrong caller is rejected and remains fail closed`() {
        val fixture = fixture()

        assertNull(fixture.coordinator.beginSession(fixture.request(), callerAuthorized = false))
        assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
        assertEquals(1L, fixture.coordinator.snapshot().wrongCallerRejects)
    }

    @Test
    fun `crash recovery requires a new generation`() {
        val fixture = fixture()
        val first = fixture.begin()
        assertEquals(ChromeGuardLeaseVerification.Accepted, fixture.heartbeat(first))

        fixture.coordinator.revoke("main_process_lost")
        val stale = fixture.heartbeat(first)
        assertTrue(stale is ChromeGuardLeaseVerification.Rejected)

        val second = fixture.begin()
        assertNotEquals(first, second)
        assertEquals(ChromeGuardLeaseVerification.Accepted, fixture.heartbeat(second, sequence = 1L))
    }

    @Test
    fun `package replacement and boot invalidation reject old lease`() {
        listOf("package_replaced_guard", "boot_guard").forEach { reason ->
            val fixture = fixture()
            val generation = fixture.begin()
            assertEquals(ChromeGuardLeaseVerification.Accepted, fixture.heartbeat(generation))

            fixture.coordinator.revoke(reason)

            assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
            assertTrue(fixture.heartbeat(generation) is ChromeGuardLeaseVerification.Rejected)
        }
    }

    @Test
    fun `repeated suspension is idempotent at effective state`() {
        val fixture = fixture()

        assertTrue(fixture.coordinator.initialize("boot_guard"))
        assertTrue(fixture.coordinator.revoke("boot_guard"))
        assertEquals(ChromeGuardState.Suspended, fixture.coordinator.snapshot().state)
        assertTrue(fixture.suspension.suspendCalls >= 2)
        assertEquals(0, fixture.suspension.releaseCalls)
    }

    @Test
    fun `storage migration timer and retry policies stay bounded`() {
        assertTrue(ChromeGuardStorageMigration.requiresReset(0))
        assertFalse(ChromeGuardStorageMigration.requiresReset(ChromeGuardContract.SchemaVersion))
        assertTrue(ChromeGuardContract.HeartbeatIntervalMillis < ChromeGuardContract.LeaseTtlMillis)
        assertEquals(1_000L, chromeGuardDeadlineDelayMillis(11_000L, 10_000L))
        assertEquals(0L, chromeGuardDeadlineDelayMillis(9_000L, 10_000L))
        assertEquals(
            ChromeGuardContract.LeaseTtlMillis,
            chromeGuardDeadlineDelayMillis(20_000L, 10_000L),
        )
        assertFalse(suspensionMutationRequired(current = true, desired = true))
        assertFalse(suspensionMutationRequired(current = false, desired = false))
        assertTrue(suspensionMutationRequired(current = null, desired = true))
        assertTrue(suspensionMutationRequired(current = false, desired = true))
        var attempts = 0
        assertTrue(
            boundedSuspensionAttempt(3) {
                attempts++
                attempts == 3
            },
        )
        assertEquals(3, attempts)
    }

    private fun fixture(
        suspendSucceeds: Boolean = true,
        releaseSucceeds: Boolean = true,
    ): Fixture {
        val store = FakeStore()
        val suspension = FakeSuspension(suspendSucceeds, releaseSucceeds)
        val coordinator = ChromeGuardCoordinator(store, suspension) { BootMarker }
        coordinator.initialize()
        return Fixture(coordinator, store, suspension)
    }

    private data class Fixture(
        val coordinator: ChromeGuardCoordinator,
        val store: FakeStore,
        val suspension: FakeSuspension,
    ) {
        fun request() = ChromeGuardSessionRequest("session", "nonce", BootMarker, 1)

        fun begin(): Long = checkNotNull(coordinator.beginSession(request(), callerAuthorized = true))

        fun heartbeat(
            generation: Long,
            sequence: Long = 1L,
        ): ChromeGuardLeaseVerification =
            coordinator.heartbeat(
                lease =
                    ChromeGuardLease(
                        protectionGeneration = generation,
                        sessionId = "session",
                        mainProcessNonce = "nonce",
                        bootMarker = BootMarker,
                        heartbeatSequence = sequence,
                        issuedAtElapsedRealtime = Now,
                        expiresAtElapsedRealtime = Now + ChromeGuardContract.LeaseTtlMillis,
                        transportGeneration = generation,
                        proxyGeneration = generation,
                        bootstrapGeneration = 1,
                        health = ChromeGuardHealth(true, true, true, true, true, true, true),
                    ),
                nowElapsed = Now,
                callerAuthorized = true,
            )
    }

    private class FakeStore : ChromeGuardGenerationStore {
        private var generation = 0L

        override fun nextGeneration(): Long = ++generation

        override fun recordState(
            suspended: Boolean,
            reason: String,
            bootMarker: Long,
        ) = Unit

        override fun incrementRestartCount(): Long = 1L
    }

    private class FakeSuspension(
        var suspendSucceeds: Boolean,
        private val releaseSucceeds: Boolean,
    ) : ChromeSuspensionPort {
        var suspendCalls = 0
        var releaseCalls = 0

        override fun ensureSuspended(reason: String): Boolean {
            suspendCalls++
            return suspendSucceeds
        }

        override fun ensureReleased(): Boolean {
            releaseCalls++
            return releaseSucceeds
        }
    }

    private companion object {
        const val Now = 20_000L
        const val BootMarker = 50L
    }
}
