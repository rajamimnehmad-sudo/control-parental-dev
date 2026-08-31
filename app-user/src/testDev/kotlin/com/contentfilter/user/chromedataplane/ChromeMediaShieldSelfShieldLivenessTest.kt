package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldSelfShieldLivenessTest {
    @Test
    fun `exact release parser and original sequence is one shot`() {
        val liveness = ChromeMediaShieldSelfShieldLiveness()
        liveness.arm(Token, Identity)

        assertTrue(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted))
        assertTrue(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.ParserContinued))
        assertTrue(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.OriginalScriptStarted))
        assertFalse(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.OriginalScriptStarted))

        assertEquals(
            ChromeMediaShieldSelfShieldLivenessMetrics(
                releaseCompleted = 1L,
                parserContinued = 1L,
                originalScriptStarted = 1L,
                rejected = 1L,
                outstanding = 0,
            ),
            liveness.metrics(),
        )
    }

    @Test
    fun `out of order stale and cross document traces never advance`() {
        val liveness = ChromeMediaShieldSelfShieldLiveness()
        liveness.arm(Token, Identity)

        assertFalse(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.ParserContinued))
        assertFalse(
            liveness.claim(
                Token,
                Identity.copy(documentSequence = 8L),
                ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted,
            ),
        )
        assertTrue(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted))
        assertEquals(1, liveness.metrics().outstanding)
    }

    @Test
    fun `trace registry remains bounded`() {
        val liveness = ChromeMediaShieldSelfShieldLiveness(maximumDocuments = 2)
        liveness.arm(Token, Identity)
        liveness.arm(SecondToken, Identity.copy(documentSequence = 8L))
        liveness.arm(ThirdToken, Identity.copy(documentSequence = 9L))

        assertEquals(2, liveness.metrics().outstanding)
        assertFalse(liveness.claim(Token, Identity, ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted))
    }

    private companion object {
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val SecondToken = "BBBBBBBBBBBBBBBBBBBBBB"
        const val ThirdToken = "CCCCCCCCCCCCCCCCCCCCCC"
        val Identity =
            ChromeMediaShieldSelfReadyIdentity(
                protectionSessionId = "session",
                policyEpoch = 20L,
                navigationSequence = 3L,
                documentSequence = 7L,
                lifecycleSequence = 1L,
                topLevel = true,
            )
    }
}
