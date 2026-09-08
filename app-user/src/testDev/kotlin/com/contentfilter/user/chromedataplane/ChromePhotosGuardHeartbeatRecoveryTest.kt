package com.contentfilter.user.chromedataplane

import com.contentfilter.user.chromeguard.ChromeGuardContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosGuardHeartbeatRecoveryTest {
    @Test
    fun `IPC timeout leaves the caller alive for a later fresh session`() =
        runBlocking<Unit> {
            val noReply = CompletableDeferred<Int>()
            assertNull(openGuardSessionOrRetry { withTimeout(10L) { noReply.await() } })
            assertEquals(7, openGuardSessionOrRetry { 7 })
        }

    @Test
    fun `parent timeout cancels recovery instead of becoming a retry`() =
        runBlocking<Unit> {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10L) {
                    openGuardSessionOrRetry { CompletableDeferred<Int>().await() }
                }
            }
        }

    @Test
    fun `external cancellation never becomes a recovery retry`() =
        runBlocking<Unit> {
            assertFailsWith<CancellationException> {
                openGuardSessionOrRetry<Int> { throw CancellationException("manual_stop") }
            }
        }

    @Test
    fun `live session is retained but elapsed lease requires a fresh generation`() {
        val recovery = ChromePhotosGuardHeartbeatRecovery()
        recovery.start()
        assertFalse(recovery.needsNewSession(10_000L, 0L))
        assertFalse(recovery.needsNewSession(10_000L + ChromeGuardContract.LeaseTtlMillis - 1L, 10_000L))
        assertTrue(recovery.needsNewSession(10_000L + ChromeGuardContract.LeaseTtlMillis, 10_000L))
        assertTrue(recovery.needsNewSession(50_000L, 10_000L))
        // A new session has no heartbeat yet; it must reach fresh health validation, not reopen forever.
        assertFalse(recovery.needsNewSession(50_000L, 0L))
    }

    @Test
    fun `persistent suspension detects rejected heartbeats even when sends stay timely`() {
        val recovery = ChromePhotosGuardHeartbeatRecovery()
        recovery.start()
        assertFalse(recovery.needsNewSession(10_000L, 9_900L, true))
        assertFalse(recovery.needsNewSession(11_000L, 10_900L, true))
        assertTrue(recovery.needsNewSession(11_500L, 11_400L, true))
        assertFalse(recovery.needsNewSession(11_600L, 0L, true))
        assertFalse(recovery.needsNewSession(12_000L, 11_900L, false))
        assertFalse(recovery.needsNewSession(12_100L, 12_000L, true))
    }

    @Test
    fun `explicit stop preparation and health failure disable automatic renewal`() {
        val recovery = ChromePhotosGuardHeartbeatRecovery()
        assertFalse(recovery.needsNewSession(50_000L, 10_000L))
        recovery.start()
        assertTrue(recovery.needsNewSession(50_000L, 10_000L))
        recovery.stop()
        assertFalse(recovery.isEnabled())
        assertFalse(recovery.needsNewSession(50_000L, 10_000L))
        assertFalse(recovery.requestDelay())
    }

    @Test
    fun `fault injection is bounded consumed once and discarded across stop start`() {
        val recovery = ChromePhotosGuardHeartbeatRecovery()
        recovery.start()
        assertEquals(0L, recovery.consumeDelay())
        assertTrue(recovery.requestDelay())
        assertFalse(recovery.requestDelay())
        assertEquals(3_000L, recovery.consumeDelay())
        assertEquals(0L, recovery.consumeDelay())
        assertTrue(recovery.requestDelay())
        recovery.stop()
        recovery.start()
        assertEquals(0L, recovery.consumeDelay())
    }
}
