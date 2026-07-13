package com.contentfilter.feature.vpn.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedConnectionInvalidationGateTest {
    @Test
    fun `first blocked decision invalidates reused browser connections`() {
        val gate = BlockedConnectionInvalidationGate(cooldownMillis = 30_000L)

        assertTrue(gate.tryAcquire(nowMillis = 1_000L))
    }

    @Test
    fun `retries inside cooldown do not reconnect repeatedly`() {
        val gate = BlockedConnectionInvalidationGate(cooldownMillis = 30_000L)

        assertTrue(gate.tryAcquire(nowMillis = 1_000L))
        assertFalse(gate.tryAcquire(nowMillis = 30_999L))
        assertTrue(gate.tryAcquire(nowMillis = 31_000L))
    }
}
