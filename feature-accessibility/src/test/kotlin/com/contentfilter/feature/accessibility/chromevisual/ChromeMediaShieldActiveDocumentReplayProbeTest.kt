package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChromeMediaShieldActiveDocumentReplayProbeTest {
    @Test
    fun `consumed current present re-enters normal boundary and is rejected without release`() {
        val probe = ChromeMediaShieldActiveDocumentReplayProbe()
        val request = request()
        var releaseCurrent = 1
        var releaseCalls = 0
        probe.rememberConsumedPresent(AttemptSequence, request)

        val result =
            probe.replay(AttemptSequence) { replay, completion ->
                if (
                    ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                        expectedClaim = Claim,
                        expectedChallenge = Challenge,
                        expectedStage = ChromeMediaShieldActiveDocumentAttemptStage.Released,
                        request = replay,
                    )
                ) {
                    releaseCalls += 1
                    releaseCurrent += 1
                    completion.acceptPresentation()
                } else {
                    completion.reject()
                }
            }

        assertEquals(ChromeMediaShieldActiveDocumentReplayResult.Rejected, result)
        assertEquals(0, releaseCalls)
        assertEquals(1, releaseCurrent)
        assertEquals(1L, probe.rejectedReplayCount())
        assertFalse(probe.hasCandidate())
    }

    @Test
    fun `replay is one shot and a second command creates no signal`() {
        val probe = ChromeMediaShieldActiveDocumentReplayProbe()
        var dispatches = 0
        probe.rememberConsumedPresent(AttemptSequence, request())

        assertEquals(
            ChromeMediaShieldActiveDocumentReplayResult.Rejected,
            probe.replay(AttemptSequence) { _, completion ->
                dispatches += 1
                completion.reject()
            },
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentReplayResult.Absent,
            probe.replay(AttemptSequence) { _, _ -> dispatches += 1 },
        )
        assertEquals(1, dispatches)
        assertEquals(1L, probe.rejectedReplayCount())
    }

    @Test
    fun `stale owner and stop clear candidate without boundary dispatch`() {
        val probe = ChromeMediaShieldActiveDocumentReplayProbe()
        var dispatches = 0
        probe.rememberConsumedPresent(AttemptSequence, request())

        assertEquals(
            ChromeMediaShieldActiveDocumentReplayResult.Stale,
            probe.replay(AttemptSequence + 1L) { _, _ -> dispatches += 1 },
        )
        assertEquals(0, dispatches)
        assertEquals(0L, probe.rejectedReplayCount())

        probe.rememberConsumedPresent(AttemptSequence, request())
        probe.clear()
        assertEquals(
            ChromeMediaShieldActiveDocumentReplayResult.Absent,
            probe.replay(AttemptSequence) { _, _ -> dispatches += 1 },
        )
        assertEquals(0, dispatches)
    }

    @Test
    fun `protocol outputs and request rendering never disclose retained challenge`() {
        val request = request()

        assertFalse(request.toString().contains(RawChallenge))
        ChromeMediaShieldActiveDocumentReplayResult.entries.forEach { result ->
            assertFalse(result.protocolResult.contains(RawChallenge))
            assertFalse(result.protocolResult.contains(Claim.identity.tokenDigest))
        }
    }

    private fun request() = ChromeMediaShieldActiveDocumentRequest.Present(Claim, Challenge)

    private companion object {
        const val AttemptSequence = 7L
        const val RawChallenge = "0123456789abcdef0123456789abcdef01234567890"
        val Challenge = ChromeMediaShieldActiveDocumentChallenge.fromEncoded(RawChallenge)
        val Claim =
            ChromeMediaShieldReadyClaim(
                identity =
                    ChromeMediaShieldDocumentIdentity(
                        protectionSessionId = "session",
                        policyEpoch = 19L,
                        navigationSequence = 2L,
                        documentSequence = 3L,
                        tokenDigest = "a".repeat(64),
                        topLevel = true,
                    ),
                lifecycleSequence = 4L,
            )
    }
}
