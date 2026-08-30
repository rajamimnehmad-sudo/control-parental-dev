package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldReadyEventReleaseGateTest {
    @Test
    fun `opaque commit then exact focus permits one release attempt`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()

        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onClaim(FirstClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onOpaqueCommitted(FirstClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.AttemptRelease, gate.onFocusBound(FirstClaim))
        assertTrue(gate.canAttemptRelease(FirstClaim))
        assertTrue(gate.commitRelease(FirstClaim) { true })
        assertFalse(gate.canAttemptRelease(FirstClaim))
        assertFalse(gate.commitRelease(FirstClaim) { true })
    }

    @Test
    fun `focus before opaque commit remains protected until same claim commits`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)

        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onFocusBound(FirstClaim))
        assertFalse(gate.canAttemptRelease(FirstClaim))
        assertEquals(
            ChromeMediaShieldReadyReleaseAction.AttemptRelease,
            gate.onOpaqueCommitted(FirstClaim),
        )
    }

    @Test
    fun `stale and background claims cannot release the active foreground claim`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)
        gate.onOpaqueCommitted(FirstClaim)

        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onFocusBound(SecondClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onOpaqueCommitted(SecondClaim))
        assertFalse(gate.canAttemptRelease(SecondClaim))
        assertFalse(gate.commitRelease(SecondClaim) { true })
        assertEquals(ChromeMediaShieldReadyReleaseAction.AttemptRelease, gate.onFocusBound(FirstClaim))
    }

    @Test
    fun `new claim revokes old one and needs its own opaque commit and focus`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)
        gate.onOpaqueCommitted(FirstClaim)
        gate.onFocusBound(FirstClaim)
        assertTrue(gate.commitRelease(FirstClaim) { true })

        assertEquals(ChromeMediaShieldReadyReleaseAction.Revoke, gate.onClaim(SecondClaim))
        assertFalse(gate.canAttemptRelease(FirstClaim))
        assertFalse(gate.canAttemptRelease(SecondClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onOpaqueCommitted(SecondClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.AttemptRelease, gate.onFocusBound(SecondClaim))
    }

    @Test
    fun `rotation protects first and retains focus but root replacement does not`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)
        gate.onOpaqueCommitted(FirstClaim)
        gate.onFocusBound(FirstClaim)
        gate.commitRelease(FirstClaim) { true }

        assertEquals(
            ChromeMediaShieldReadyReleaseAction.Revoke,
            gate.onSurfaceInvalidated(retainFocus = true),
        )
        assertEquals(
            ChromeMediaShieldReadyReleaseAction.AttemptRelease,
            gate.onOpaqueCommitted(FirstClaim),
        )

        gate.onSurfaceInvalidated(retainFocus = false)
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onOpaqueCommitted(FirstClaim))
        assertFalse(gate.canAttemptRelease(FirstClaim))
    }

    @Test
    fun `stop makes every late callback permanently inert`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)
        gate.onOpaqueCommitted(FirstClaim)

        assertEquals(ChromeMediaShieldReadyReleaseAction.Revoke, gate.close())
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onFocusBound(FirstClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onOpaqueCommitted(FirstClaim))
        assertEquals(ChromeMediaShieldReadyReleaseAction.Ignore, gate.onClaim(SecondClaim))
        assertFalse(gate.canAttemptRelease(FirstClaim))
        assertFalse(gate.commitRelease(FirstClaim) { true })
    }

    @Test
    fun `failed boundary commit remains protected and pending without an automatic retry`() {
        val gate = ChromeMediaShieldReadyEventReleaseGate()
        gate.onClaim(FirstClaim)
        gate.onOpaqueCommitted(FirstClaim)
        gate.onFocusBound(FirstClaim)
        var commits = 0

        assertFalse(
            gate.commitRelease(FirstClaim) {
                commits += 1
                false
            },
        )
        assertEquals(1, commits)
        assertTrue(gate.canAttemptRelease(FirstClaim))
        assertEquals(1, commits)

        assertTrue(
            gate.commitRelease(FirstClaim) {
                commits += 1
                true
            },
        )
        assertEquals(2, commits)
        assertFalse(gate.canAttemptRelease(FirstClaim))
    }

    @Test
    fun `viewport replacement retains before prepare while window replacement revokes`() {
        val portrait = ChromeVisualViewport(0, 100, 1080, 2200)
        val landscape = ChromeVisualViewport(0, 70, 2340, 1030)

        val rotation =
            ChromeMediaShieldReadyViewportTransitionPolicy.decide(
                currentActive = true,
                currentWindowId = 17,
                currentViewport = portrait,
                nextWindowId = 17,
                nextViewport = landscape,
            )
        assertTrue(rotation.retainCurrentDocument)
        assertFalse(rotation.revokeBeforePrepare)

        val replacement =
            ChromeMediaShieldReadyViewportTransitionPolicy.decide(
                currentActive = true,
                currentWindowId = 17,
                currentViewport = portrait,
                nextWindowId = 18,
                nextViewport = landscape,
            )
        assertFalse(replacement.retainCurrentDocument)
        assertTrue(replacement.revokeBeforePrepare)
    }

    @Test
    fun `same viewport and inactive context cannot retain a ready document`() {
        val viewport = ChromeVisualViewport(0, 100, 1080, 2200)

        listOf(
            ChromeMediaShieldReadyViewportTransitionPolicy.decide(
                currentActive = true,
                currentWindowId = 17,
                currentViewport = viewport,
                nextWindowId = 17,
                nextViewport = viewport,
            ),
            ChromeMediaShieldReadyViewportTransitionPolicy.decide(
                currentActive = false,
                currentWindowId = 17,
                currentViewport = viewport,
                nextWindowId = 17,
                nextViewport = viewport.copy(bottom = viewport.bottom + 1),
            ),
        ).forEach { transition ->
            assertFalse(transition.retainCurrentDocument)
            assertTrue(transition.revokeBeforePrepare)
        }
    }

    private companion object {
        val FirstClaim = claim(token = "a", documentSequence = 1L)
        val SecondClaim = claim(token = "b", documentSequence = 2L)

        fun claim(
            token: String,
            documentSequence: Long,
        ) = ChromeMediaShieldReadyClaim(
            identity =
                ChromeMediaShieldDocumentIdentity(
                    protectionSessionId = "h19-session",
                    policyEpoch = 19L,
                    navigationSequence = documentSequence,
                    documentSequence = documentSequence,
                    tokenDigest = token.repeat(64),
                    topLevel = true,
                ),
            lifecycleSequence = 1L,
        )
    }
}
