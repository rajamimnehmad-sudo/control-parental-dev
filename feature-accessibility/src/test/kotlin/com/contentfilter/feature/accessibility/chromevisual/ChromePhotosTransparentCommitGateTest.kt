package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosTransparentCommitGateTest {
    @Test
    fun `success callback is delivered only after transaction commit`() {
        val results = mutableListOf<Boolean>()
        val gate = ChromePhotosTransparentCommitGate()
        val token = gate.begin(results::add)

        assertTrue(results.isEmpty())
        assertEquals(
            ChromePhotosTransparentCommitOutcome.Committed,
            gate.onTransactionCommitted(
                token = token,
                boundaryCurrent = { true },
                commitCurrent = { true },
            ),
        )
        assertEquals(listOf(true), results)
    }

    @Test
    fun `boundary recheck failure is fail closed and never commits authority`() {
        val results = mutableListOf<Boolean>()
        var authorityCommits = 0
        val gate = ChromePhotosTransparentCommitGate()
        val token = gate.begin(results::add)

        assertEquals(
            ChromePhotosTransparentCommitOutcome.RejectedCurrent,
            gate.onTransactionCommitted(
                token = token,
                boundaryCurrent = { false },
                commitCurrent = {
                    authorityCommits += 1
                    true
                },
            ),
        )
        assertEquals(0, authorityCommits)
        assertEquals(listOf(false), results)
    }

    @Test
    fun `surface invalidation completes fail closed and makes late callback inert`() {
        val results = mutableListOf<Boolean>()
        var authorityCommits = 0
        val gate = ChromePhotosTransparentCommitGate()
        val stale = gate.begin(results::add)

        assertTrue(gate.invalidate())
        assertEquals(listOf(false), results)
        assertEquals(
            ChromePhotosTransparentCommitOutcome.Stale,
            gate.onTransactionCommitted(
                token = stale,
                boundaryCurrent = { true },
                commitCurrent = {
                    authorityCommits += 1
                    true
                },
            ),
        )
        assertEquals(0, authorityCommits)
        assertEquals(listOf(false), results)
    }

    @Test
    fun `new transaction supersedes old callback without affecting current authority`() {
        val staleResults = mutableListOf<Boolean>()
        val currentResults = mutableListOf<Boolean>()
        val gate = ChromePhotosTransparentCommitGate()
        val stale = gate.begin(staleResults::add)
        val current = gate.begin(currentResults::add)

        assertEquals(listOf(false), staleResults)
        assertEquals(
            ChromePhotosTransparentCommitOutcome.Stale,
            gate.onTransactionCommitted(
                token = stale,
                boundaryCurrent = { true },
                commitCurrent = { true },
            ),
        )
        assertTrue(currentResults.isEmpty())
        assertEquals(
            ChromePhotosTransparentCommitOutcome.Committed,
            gate.onTransactionCommitted(
                token = current,
                boundaryCurrent = { true },
                commitCurrent = { true },
            ),
        )
        assertEquals(listOf(true), currentResults)
    }

    @Test
    fun `failed policy commit reports failure exactly once`() {
        val results = mutableListOf<Boolean>()
        val gate = ChromePhotosTransparentCommitGate()
        val token = gate.begin(results::add)

        assertEquals(
            ChromePhotosTransparentCommitOutcome.RejectedCurrent,
            gate.onTransactionCommitted(
                token = token,
                boundaryCurrent = { true },
                commitCurrent = { false },
            ),
        )
        assertEquals(listOf(false), results)
        assertFalse(gate.reject(token))
        assertEquals(listOf(false), results)
    }
}
