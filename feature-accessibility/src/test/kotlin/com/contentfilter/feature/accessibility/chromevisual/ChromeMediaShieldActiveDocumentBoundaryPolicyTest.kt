package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldActiveDocumentBoundaryPolicyTest {
    @Test
    fun `transport cancellation before main dispatch cannot create an attempt`() {
        val guard = ChromeMediaShieldActiveDocumentDispatchGuard()

        guard.cancel()

        assertFalse(
            guard.mayDispatch(
                ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered,
            ),
        )
        assertFalse(
            ChromeMediaShieldActiveDocumentDispatchGuard().mayDispatch(
                ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled,
            ),
        )
        assertFalse(
            ChromeMediaShieldActiveDocumentDispatchGuard().mayDispatch(
                ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCompleted,
            ),
        )
        assertTrue(
            ChromeMediaShieldActiveDocumentDispatchGuard().mayDispatch(
                ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered,
            ),
        )
    }

    @Test
    fun `transport cancelled before transparent commit is not current`() {
        val delegate = RecordingCompletion()
        val completion = ChromeMediaShieldActiveDocumentGuardedCompletion(delegate)

        assertTrue(
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                completion,
            ),
        )
        assertTrue(completion.cancelTransport())

        assertFalse(
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                completion,
            ),
        )
        assertFalse(completion.acceptPresentation())
        assertEquals(0, delegate.presentationsAccepted)
    }

    @Test
    fun `transport cancelled between platform callback and postcommit loses authority`() {
        val delegate = RecordingCompletion()
        val completion = ChromeMediaShieldActiveDocumentGuardedCompletion(delegate)
        val platformCallbackObservedCurrent =
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                completion,
            )

        assertTrue(platformCallbackObservedCurrent)
        assertTrue(completion.cancelTransport())

        val postcommitStillCurrent =
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                completion,
            )
        assertFalse(postcommitStillCurrent)
        assertFalse(completion.acceptPresentation())
        assertEquals(0, delegate.presentationsAccepted)
    }

    @Test
    fun `cancelled completion rejects every late release callback and exact token mismatch`() {
        val delegate = RecordingCompletion()
        val completion = ChromeMediaShieldActiveDocumentGuardedCompletion(delegate)
        val other = ChromeMediaShieldActiveDocumentGuardedCompletion(RecordingCompletion())

        assertFalse(
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                other,
            ),
        )
        assertTrue(completion.cancelTransport())
        assertFalse(completion.cancelTransport())

        repeat(3) { assertFalse(completion.acceptPresentation()) }
        assertEquals(0, delegate.presentationsAccepted)
        assertFalse(
            ChromeMediaShieldActiveDocumentTransportBoundaryPolicy.isCurrent(
                completion,
                completion,
            ),
        )
    }

    @Test
    fun `prove present and revoke require exact phase claim and challenge`() {
        val challenge = ChromeMediaShieldActiveDocumentChallenge.fromEncoded("c".repeat(43))
        val other = ChromeMediaShieldActiveDocumentChallenge.fromEncoded("d".repeat(43))

        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Challenged,
                ChromeMediaShieldActiveDocumentRequest.Prove(Claim, challenge),
            ),
        )
        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Proved,
                ChromeMediaShieldActiveDocumentRequest.Present(Claim, challenge),
            ),
        )
        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Released,
                ChromeMediaShieldActiveDocumentRequest.Revoke(Claim, challenge),
            ),
        )
        assertFalse(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Challenged,
                ChromeMediaShieldActiveDocumentRequest.Present(Claim, challenge),
            ),
        )
        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Held,
                ChromeMediaShieldActiveDocumentRequest.Revoke(Claim, challenge),
            ),
        )
        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Committing,
                ChromeMediaShieldActiveDocumentRequest.Revoke(Claim, challenge),
            ),
        )
        assertFalse(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                null,
                ChromeMediaShieldActiveDocumentAttemptStage.Held,
                ChromeMediaShieldActiveDocumentRequest.Revoke(Claim, challenge),
            ),
        )
        assertFalse(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.acceptsRequest(
                Claim,
                challenge,
                ChromeMediaShieldActiveDocumentAttemptStage.Proved,
                ChromeMediaShieldActiveDocumentRequest.Present(Claim, other),
            ),
        )
    }

    @Test
    fun `surface viewport window and native root are all exact`() {
        val expected = binding()
        val surface = snapshot()
        assertTrue(
            ChromeMediaShieldActiveDocumentBoundaryPolicy.isExactBoundary(
                expected,
                expected,
                surface,
                surface,
                claimCurrent = true,
                attestationCurrent = true,
            ),
        )
        listOf(
            expected.copy(windowId = 18),
            expected.copy(viewport = expected.viewport.copy(right = 719)),
            expected.copy(nativeRootDigest = "c".repeat(64)),
            expected.copy(nativeRootBindingKind = ChromeMediaShieldNativeRootBindingKind.PlatformUniqueId),
        ).forEach { observed ->
            assertFalse(
                ChromeMediaShieldActiveDocumentBoundaryPolicy.isExactBoundary(
                    expected,
                    observed,
                    surface,
                    surface,
                    claimCurrent = true,
                    attestationCurrent = true,
                ),
            )
        }
    }

    @Test
    fun `hello waits for a structural event and transfers its original completion exactly once`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        val waiting = mutableListOf<String>()
        val accepted = mutableListOf<ChromeMediaShieldActiveDocumentHandshakeCompletion>()
        val completion = RecordingCompletion()
        val admission =
            ChromeMediaShieldActiveDocumentHelloAdmission(
                readContext = { context },
                claimCurrent = { true },
                onWaiting = { _, reason -> waiting += reason },
                onAccepted = { _, _, value -> accepted += value },
                onRejected = { _, _ -> error("must not reject") },
            )

        admission.accept(Claim, completion)
        assertEquals(listOf("foreground_window_unavailable"), waiting)
        assertTrue(admission.hasCurrentClaim())
        assertTrue(accepted.isEmpty())
        assertEquals(0, completion.rejected)

        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(binding())
        admission.onChromeStructuralEvent()
        admission.onChromeStructuralEvent()

        assertTrue(accepted.single() === completion)
        assertFalse(admission.hasCurrentClaim())
        assertEquals(0, completion.rejected)
    }

    @Test
    fun `stale or transport-cancelled pending hello can never promote later`() {
        var current = true
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_root_unavailable")
        val reasons = mutableListOf<String>()
        val accepted = mutableListOf<ChromeMediaShieldActiveDocumentHandshakeCompletion>()
        val stale = RecordingCompletion()
        val admission =
            ChromeMediaShieldActiveDocumentHelloAdmission(
                readContext = { context },
                claimCurrent = { current },
                onWaiting = { _, _ -> Unit },
                onAccepted = { _, _, completion -> accepted += completion },
                onRejected = { _, reason -> reasons += reason },
            )

        admission.accept(Claim, stale)
        current = false
        admission.onChromeStructuralEvent()
        assertEquals(1, stale.rejected)
        assertEquals(listOf("hello_claim_stale"), reasons)

        current = true
        val cancelled = RecordingCompletion()
        admission.accept(Claim, cancelled)
        assertTrue(admission.onTransportCancelled(cancelled))
        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(binding())
        admission.onChromeStructuralEvent()

        assertTrue(accepted.isEmpty())
        assertEquals(0, cancelled.rejected)
        assertEquals(listOf("hello_claim_stale", "handshake_transport_cancelled"), reasons)
    }

    private fun binding() =
        ChromeMediaShieldActiveDocumentNativeBinding(
            windowId = 17,
            viewport = ChromeVisualViewport(0, 0, 720, 1_500),
            nativeRootDigest = "a".repeat(64),
            nativeRootBindingKind = ChromeMediaShieldNativeRootBindingKind.RetainedNode,
        )

    private fun snapshot() =
        ChromePhotosProtectedSurfaceSnapshot(
            phase = ChromePhotosProtectedSurfacePhase.Covered,
            epoch = 3,
            windowId = 17,
            viewport = ChromeVisualViewport(0, 0, 720, 1_500),
            activeSequence = 0,
            presentedSequence = 0,
        )

    private companion object {
        val Claim =
            ChromeMediaShieldReadyClaim(
                ChromeMediaShieldDocumentIdentity("session", 19, 4, 8, "e".repeat(64), true),
                lifecycleSequence = 2,
            )
    }

    private class RecordingCompletion : ChromeMediaShieldActiveDocumentHandshakeCompletion {
        var rejected = 0
        var presentationsAccepted = 0

        override fun onTransportCancelled(callback: () -> Unit) =
            ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered

        override fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge) = false

        override fun acceptProof() = false

        override fun acceptPresentation(): Boolean {
            presentationsAccepted += 1
            return true
        }

        override fun acceptRevocation() = false

        override fun reject(): Boolean {
            rejected += 1
            return true
        }
    }
}
