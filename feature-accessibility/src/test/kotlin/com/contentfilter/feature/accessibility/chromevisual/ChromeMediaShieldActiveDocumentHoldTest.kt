package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeMediaShieldActiveDocumentHoldTest {
    @Test
    fun `hold is exact one-shot and stores only a nonce digest`() {
        val hold = ChromeMediaShieldActiveDocumentHold()
        val results = mutableListOf<Boolean>()

        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        val reached = hold.reach(ChromeMediaShieldActiveDocumentHold.PresentPrecommit, results::add)
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Reached, reached?.phase)
        assertFalse(checkNotNull(reached).nonceDigest.contains(Nonce))
        assertNull(hold.release(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, WrongNonce))
        assertTrue(results.isEmpty())
        assertEquals(
            reached,
            hold.release(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce),
        )
        assertEquals(listOf(true), results)
        assertNull(hold.release(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
    }

    @Test
    fun `cancel and replacement fail close a reached continuation exactly once`() {
        val hold = ChromeMediaShieldActiveDocumentHold()
        val results = mutableListOf<Boolean>()

        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        hold.reach(ChromeMediaShieldActiveDocumentHold.PresentPrecommit, results::add)
        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, WrongNonce))
        assertEquals(listOf(false), results)
        assertNull(hold.cancel(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Armed, hold.snapshot().phase)
        hold.cancel()
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Idle, hold.snapshot().phase)
    }

    @Test
    fun `early release malformed inputs and wrong stages are rejected`() {
        val hold = ChromeMediaShieldActiveDocumentHold()

        assertFalse(hold.arm("unknown", ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        assertFalse(hold.arm(CaseId, "unknown", Nonce))
        assertFalse(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, "short"))
        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        assertNull(hold.release(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        assertEquals(
            ChromeMediaShieldActiveDocumentHoldPhase.Armed,
            hold.cancel(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce)?.phase,
        )
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Idle, hold.snapshot().phase)
    }

    @Test
    fun `postcommit race hold is an explicit one-shot stage`() {
        val hold = ChromeMediaShieldActiveDocumentHold()
        val results = mutableListOf<Boolean>()

        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPostcommit, Nonce))
        assertEquals(
            ChromeMediaShieldActiveDocumentHoldPhase.Reached,
            hold.reach(ChromeMediaShieldActiveDocumentHold.PresentPostcommit, results::add)?.phase,
        )
        assertNotNull(
            hold.cancel(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPostcommit, Nonce),
        )
        assertEquals(listOf(false), results)
    }

    @Test
    fun `supersession fails closed old continuation and holds next attempt at same stage`() {
        val hold = ChromeMediaShieldActiveDocumentHold()
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()

        assertTrue(hold.arm(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        val original = hold.reach(ChromeMediaShieldActiveDocumentHold.PresentPrecommit, first::add)
        assertEquals(original, hold.transferToSupersedingAttempt())
        assertEquals(listOf(false), first)
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Armed, hold.snapshot().phase)

        val transferred = hold.reach(ChromeMediaShieldActiveDocumentHold.PresentPrecommit, second::add)
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Reached, transferred?.phase)
        assertEquals(original?.nonceDigest, transferred?.nonceDigest)
        assertNotNull(hold.cancel(CaseId, ChromeMediaShieldActiveDocumentHold.PresentPrecommit, Nonce))
        assertEquals(listOf(false), second)
        assertEquals(ChromeMediaShieldActiveDocumentHoldPhase.Idle, hold.snapshot().phase)
    }

    private companion object {
        const val CaseId = "switch_during_prove_present"
        const val Nonce = "0123456789abcdef0123456789abcdef"
        const val WrongNonce = "abcdef0123456789abcdef0123456789"
    }
}
